package one.chartsy.simulation.engine;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.context.ExecutionContext;
import one.chartsy.data.DataQuery;
import one.chartsy.data.DataSubscription;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.structures.PriorityMap;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.MarketMessageHandler;
import one.chartsy.messaging.MarketMessageSource;
import one.chartsy.simulation.time.PlaybackClock;
import one.chartsy.trade.algorithm.MarketSupplier;

import java.util.Set;
import java.util.stream.Collectors;

public class FlatFileDataMarketSupplier implements MarketSupplier {

    private final FlatFileDataProvider dataProvider;
    private final DataSubscription subscription;
    private final PlaybackClock clock;

    private final PriorityMap<MarketEvent, MarketMessageSource> subscribers;

    public FlatFileDataMarketSupplier(FlatFileDataProvider dataProvider, DataSubscription subscription) {
        this(dataProvider, subscription, new PlaybackClock());
    }

    public FlatFileDataMarketSupplier(FlatFileDataProvider dataProvider, DataSubscription subscription, PlaybackClock clock) {
        this.dataProvider = dataProvider;
        this.subscription = subscription;
        this.clock = clock;
        this.subscribers = new PriorityMap<>();
    }

    @Override
    public void open() {
        Set<String> symbols = !subscription.isSubscribedToAllSymbols() ? subscription.symbols() : dataProvider.listSymbols().stream()
                .map(SymbolIdentity::name)
                .collect(Collectors.toSet());

        ExecutionContext context = new ExecutionContext();
        subscribers.clear();
        for (var symbol : symbols) {
            var query = DataQuery.<Candle>builder()
                    .resource(SymbolResource.of(symbol, TimeFrame.Period.DAILY))
                    .build();
            var messageSource = dataProvider.iterator(query, context);
            var firstMessage = messageSource.getMessage();
            if (firstMessage != null)
                subscribers.put(firstMessage, messageSource);
        }

        this.clock.setTime(subscribers.peekKey());
    }

    @Override
    public int poll(MarketMessageHandler handler, int messageLimit) {
        int count = 0;

        var current = subscribers.peekKey();
        if (current != null) {
            clock.setTime(current);
            while (count < messageLimit) {
                var subscriber = subscribers.remove();
                var lastTime = current.getTime();
                handler.onMarketMessage(current);
                count++;

                var next = subscriber.getMessage();
                if (next != null) {
                    subscribers.put(next, subscriber);
                } else {
                    subscriber.close();
                }

                current = subscribers.peekKey();
                if (current == null || current.getTime() != lastTime) {
                    break;
                }
            }
        }
        return count;
    }

    @Override
    public void close() {
        subscribers.forEach((__, source) -> source.close());
        subscribers.clear();
    }
}
