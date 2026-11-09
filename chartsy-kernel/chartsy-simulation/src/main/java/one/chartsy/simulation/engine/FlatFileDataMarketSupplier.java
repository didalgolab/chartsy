package one.chartsy.simulation.engine;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.context.ExecutionContext;
import one.chartsy.data.DataQuery;
import one.chartsy.data.DataSubscription;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.messaging.MarketMessageHandler;
import one.chartsy.trade.algorithm.MarketSupplier;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stable merge of flat-file source events. Existing constructors retain raw source timestamps
 * and event types. Supply a BarSession explicitly for causal phase replay. Its input is the
 * provider's candle timestamp, which can already be a period-end timestamp rather than a date label.
 */
public class FlatFileDataMarketSupplier implements MarketSupplier {

    private final FlatFileDataProvider dataProvider;
    private final DataSubscription subscription;
    private final Instant startTime;

    private final MarketReplayMerge subscribers = new MarketReplayMerge();
    private final BarSession sessions;

    public FlatFileDataMarketSupplier(FlatFileDataProvider dataProvider, DataSubscription subscription) {
        this(dataProvider, subscription, null);
    }

    public FlatFileDataMarketSupplier(FlatFileDataProvider dataProvider, DataSubscription subscription, Instant startTime) {
        this(dataProvider, subscription, startTime, (BarSession) null);
    }

    public FlatFileDataMarketSupplier(FlatFileDataProvider dataProvider, DataSubscription subscription,
                                      Instant startTime, BarSession sessions) {
        this.dataProvider = dataProvider;
        this.subscription = subscription;
        this.startTime = startTime;
        this.sessions = sessions;
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
        subscribers.close();
        int ordinal = 0;
        for (var symbol : symbols.stream().sorted().toList()) {
            var query = queryBuilder
                    .resource(SymbolResource.of(symbol, TimeFrame.Period.DAILY))
                    .build();
            var messageSource = dataProvider.iterator(query, context);
            subscribers.add(sessions == null ? messageSource : new BarReplaySource(messageSource, sessions), ordinal++);
        }
    }

    @Override
    public int poll(MarketMessageHandler handler, int pollLimit) {
        return subscribers.poll(handler, pollLimit);
    }

    @Override
    public void close() {
        subscribers.close();
    }
}
