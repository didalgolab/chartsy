package one.chartsy.simulation.engine;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.base.Disposable;
import one.chartsy.context.ExecutionContext;
import one.chartsy.data.DataQuery;
import one.chartsy.data.DataSubscription;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.structures.PriorityMap;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.MarketMessageHandler;
import one.chartsy.messaging.MarketMessageSource;
import one.chartsy.trade.algorithm.MarketSupplier;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.stream.Collectors;

public class FlatFileDataMarketSupplier implements MarketSupplier {

    private final FlatFileDataProvider dataProvider;
    private final DataSubscription subscription;
    private final Instant startTime;

    private final PriorityMap<MarketEvent, MarketMessageSource> subscribers;

    public FlatFileDataMarketSupplier(FlatFileDataProvider dataProvider, DataSubscription subscription) {
        this(dataProvider, subscription, null);
    }

    public FlatFileDataMarketSupplier(FlatFileDataProvider dataProvider, DataSubscription subscription, Instant startTime) {
        this.dataProvider = dataProvider;
        this.subscription = subscription;
        this.startTime = startTime;
        this.subscribers = new PriorityMap<>();
    }

    @Override
    public void open() {
        Set<String> symbols = !subscription.isSubscribedToAllSymbols() ? subscription.symbols() : dataProvider.listSymbols().stream()
                .map(SymbolIdentity::name)
                .collect(Collectors.toSet());

        var queryBuilder = DataQuery.<Candle>builder();
        if (startTime != null)
            queryBuilder.startTime(LocalDateTime.ofInstant(startTime, ZoneOffset.UTC));

        ExecutionContext context = new ExecutionContext();
        subscribers.clear();
        for (var symbol : symbols) {
            var query = queryBuilder
                    .resource(SymbolResource.of(symbol, TimeFrame.Period.DAILY))
                    .build();
            var messageSource = dataProvider.iterator(query, context);
            var firstMessage = messageSource.getMessage();
            if (firstMessage != null)
                subscribers.put(firstMessage, messageSource);
        }
    }

    @Override
    public int poll(MarketMessageHandler handler, int pollLimit) {
        int count = 0;

        var current = subscribers.peekKey();
        if (current != null) {
            while (count < pollLimit) {
                var subscriber = subscribers.remove();
                var lastTime = current.time();
                handler.onMarketMessage(current);
                count++;

                var next = subscriber.getMessage();
                if (next != null) {
                    subscribers.put(next, subscriber);
                } else {
                    subscriber.close();
                }

                current = subscribers.peekKey();
                if (current == null || current.time() != lastTime) {
                    break;
                }
            }
        }
        return count;
    }

    @Override
    public void close() {
        subscribers.forEachValue(Disposable::close);
        subscribers.clear();
    }
}
