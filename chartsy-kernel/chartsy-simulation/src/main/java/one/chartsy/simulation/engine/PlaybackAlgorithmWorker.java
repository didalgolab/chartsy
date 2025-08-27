/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation.engine;

import one.chartsy.data.stream.MessageBuffer;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.MarketMessageHandler;
import one.chartsy.service.ServiceWorker;
import one.chartsy.simulation.engine.price.PlaybackMarketPriceService;
import one.chartsy.simulation.messaging.PlaybackMarketMessageHandler;
import one.chartsy.simulation.time.PlaybackClock;
import one.chartsy.trade.algorithm.Algorithm;
import one.chartsy.trade.algorithm.AlgorithmWorker;
import one.chartsy.trade.algorithm.MarketSupplier;
import one.chartsy.trade.service.connector.TradeConnector;

public class PlaybackAlgorithmWorker extends AlgorithmWorker implements MarketMessageHandler {

    private final PlaybackMarketMessageHandler marketMessageHandler;
    private final PlaybackMarketPriceService priceService;
    private final MarketMessageHandler tradeSimulator;
    private final ServiceWorker<? extends TradeConnector> tradeWorker;

    public PlaybackAlgorithmWorker(Algorithm algorithm, MessageBuffer queue, MarketSupplier marketSupplier, PlaybackClock clock, PlaybackMarketPriceService priceService, MarketMessageHandler tradeSimulator, ServiceWorker<? extends TradeConnector> tradeWorker) {
        super(algorithm, queue, marketSupplier);
        this.marketMessageHandler = new PlaybackMarketMessageHandler(clock, invoker.getMarketMessageHandler());
        this.priceService = priceService;
        this.tradeSimulator = tradeSimulator;
        this.tradeWorker = tradeWorker;
    }

    @Override
    protected int supplyMarketData(int workDone) {
        return marketSupplier.poll(this, DEFAULT_POLL_LIMIT);
    }

    @Override
    public void onMarketMessage(MarketEvent event) {
        tradeWorker.doWork();
        tradeSimulator.onMarketMessage(event);
        priceService.onMarketMessage(event);
        marketMessageHandler.onMarketMessage(event);
    }
}
