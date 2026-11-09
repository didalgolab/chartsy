package one.chartsy.simulation.engine;

import one.chartsy.data.stream.MessageBuffer;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.MarketMessageHandler;
import one.chartsy.messaging.data.BarOpen;
import one.chartsy.messaging.data.BestBidOfferEvent;
import one.chartsy.service.ServiceWorker;
import one.chartsy.simulation.engine.price.PlaybackMarketPriceService;
import one.chartsy.simulation.time.PlaybackClock;
import one.chartsy.trade.algorithm.Algorithm;
import one.chartsy.trade.algorithm.AlgorithmWorker;
import one.chartsy.trade.algorithm.MarketSupplier;
import one.chartsy.trade.service.connector.TradeConnector;

/** Single-threaded replay phases; clock and prices always precede broker and strategy callbacks. */
public class PlaybackAlgorithmWorker extends AlgorithmWorker implements MarketMessageHandler {
    private final PlaybackClock clock;
    private final PlaybackMarketPriceService priceService;
    private final MarketMessageHandler tradeSimulator;
    private final Runnable transferTradeRequests;
    private boolean marketExhausted;

    /** Compatibility constructor retaining the supplied worker's polling policy. */
    public PlaybackAlgorithmWorker(Algorithm algorithm, MessageBuffer queue, MarketSupplier marketSupplier,
                                   PlaybackClock clock, PlaybackMarketPriceService priceService,
                                   MarketMessageHandler tradeSimulator, ServiceWorker<? extends TradeConnector> tradeWorker) {
        this(algorithm, queue, marketSupplier, clock, priceService, tradeSimulator, tradeWorker::doWork);
    }

    /**
     * Uses a finite transfer of the requests pending at each playback phase boundary.
     * The transfer must leave requests created during delivery for a later phase.
     */
    public PlaybackAlgorithmWorker(Algorithm algorithm, MessageBuffer queue, MarketSupplier marketSupplier,
                                   PlaybackClock clock, PlaybackMarketPriceService priceService,
                                   MarketMessageHandler tradeSimulator, Runnable transferTradeRequests) {
        super(algorithm, queue, marketSupplier);
        this.clock = clock;
        this.priceService = priceService;
        this.tradeSimulator = tradeSimulator;
        this.transferTradeRequests = transferTradeRequests;
    }

    @Override protected int supplyMarketData(int workDone) {
        if (marketExhausted)
            return 0;
        int work = marketSupplier.poll(this, DEFAULT_POLL_LIMIT);
        marketExhausted = work == 0;
        return work;
    }

    public boolean isMarketExhausted() { return marketExhausted; }

    @Override public void onMarketMessage(MarketEvent event) {
        clock.setTime(event);
        priceService.onMarketMessage(event);
        transferTradeRequests.run();
        tradeSimulator.onMarketMessage(event);
        if (event instanceof BarOpen open && tradeSimulator instanceof TradingSimulator simulator) {
            // Fill-callback requests are queued before this boundary, hence cannot recurse at this opening.
            transferTradeRequests.run();
            int start = simulator.openingDecisionStart(open);
            invoker.getMarketMessageHandler().onMarketMessage(event);
            transferTradeRequests.run();
            simulator.matchOpeningOrders(open, start);
        } else {
            invoker.getMarketMessageHandler().onMarketMessage(event);
            if (event instanceof BestBidOfferEvent && tradeSimulator instanceof TradingSimulator simulator
                    && simulator.usesQuoteExecution())
                simulator.collectQuoteCallbackRequests(event.time(), transferTradeRequests);
        }
    }
}
