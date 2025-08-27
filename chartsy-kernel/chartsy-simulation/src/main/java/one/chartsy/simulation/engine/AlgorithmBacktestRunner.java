/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation.engine;

import one.chartsy.data.stream.QueuedMessageBuffer;
import one.chartsy.simulation.engine.price.PlaybackMarketPriceService;
import one.chartsy.simulation.reporting.BacktestReport;
import one.chartsy.simulation.time.PlaybackClock;
import one.chartsy.time.Chronological;
import one.chartsy.trade.algorithm.Algorithm;
import one.chartsy.trade.algorithm.AlgorithmFactory;
import one.chartsy.trade.algorithm.AlgorithmWorker;
import one.chartsy.trade.algorithm.DefaultAlgorithmContext;
import one.chartsy.trade.service.AlgorithmEngine;
import one.chartsy.trade.service.connector.TradeConnectorContext;
import one.chartsy.trade.service.connector.TradeConnectorProxy;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executes an algorithm backtest by orchestrating market data flow, algorithm execution, and performance tracking.
 * <p>
 * The {@code AlgorithmBacktestRunner} provides a standardized entry point for running algorithm simulations,
 * managing the lifecycle of both the trading algorithm and the market data supplier within a controlled,
 * deterministic environment. By abstracting common setup and execution steps, it significantly reduces
 * boilerplate code and complexity associated with conducting algorithm backtests.
 * <p>
 * Internally, the runner performs the following tasks:
 * <ul>
 *     <li>Initializes a playback environment using a {@link PlaybackClock} for precise event timing.</li>
 *     <li>Creates a bounded message queue to manage sequential market events efficiently.</li>
 *     <li>Instantiates and configures the {@link Algorithm} using the provided {@link AlgorithmFactory}.</li>
 *     <li>Obtains market data from the configured {@link MarketSupplierFactory}, ensuring time-aligned delivery.</li>
 *     <li>Executes the backtest within a single-threaded, deterministic event loop.</li>
 *     <li>Tracks execution time, facilitating performance measurement and optimization.</li>
 * </ul>
 * <p>
 * Designed primarily for use in a single-threaded context, parallel or concurrent execution of multiple algorithm
 * backtests should be managed externally, typically at a higher orchestration layer.
 *
 * @see AlgorithmBacktestContext
 * @see AlgorithmFactory
 * @see MarketSupplierFactory
 * @see AlgorithmWorker
 * @see PlaybackClock
 */
public class AlgorithmBacktestRunner {

    private static final AtomicInteger runNumber = new AtomicInteger();

    /**
     * Runs the specified trading algorithm backtest using provided factories for the algorithm and market supplier.
     * <p>
     * This method orchestrates the full lifecycle of a backtest session - from initialization of the execution environment
     * and algorithm instantiation to market data provisioning and sequential event processing - until all events are
     * consumed or an algorithm-induced shutdown occurs.
     *
     * @param configuration the algorithm and the backtest run configurations
     */
    public BacktestReport run(AlgorithmBacktestContext configuration) {
        var startTime = System.nanoTime();
        var clock = new PlaybackClock();
        var queue = new QueuedMessageBuffer<>(1024);
        var engine = new AlgorithmEngine(clock);
        var context = new DefaultAlgorithmContext(configuration.algorithmName(), clock, queue, engine, engine.getSequenceGenerator());
        var algorithm = configuration.algorithmFactory().create(context);
        var priceService = new PlaybackMarketPriceService();
        var tradingContext = new TradeConnectorContext("SIMULATOR", clock, priceService, engine);
        var tradingSimulator = new TradingSimulator(tradingContext);
        var tradingServiceProxy = new TradeConnectorProxy(tradingSimulator, new ArrayBlockingQueue<>(1024));
        var tradingWorker = tradingServiceProxy.getWorker();
        var worker = new PlaybackAlgorithmWorker(algorithm, queue, configuration.marketSupplierFactory().create(), clock, priceService, tradingSimulator, tradingWorker);

        context.addShutdownResponseHandler(engine);
        engine.addAlgorithm(algorithm);
        engine.addTradeConnector(tradingServiceProxy);

        try {
            worker.onOpen();
            tradingWorker.onOpen();
            while (worker.doWork() > 0)
                if (engine.isShutdown())
                    break;

        } finally {
            worker.onClose();
            tradingWorker.onClose();
        }

        long elapsedMillis = (System.nanoTime() - startTime) / 1_000_000;
        double elapsedSeconds = elapsedMillis / 1000.0;
        System.out.println("Elapsed time [sec]: " + elapsedSeconds);
        return createBacktestReport(tradingSimulator, elapsedSeconds);
    }

    private BacktestReport createBacktestReport(TradingSimulator tradingSimulator, double elapsedSeconds) {
        return new BacktestReport.Of(
                tradingSimulator.getDefaultAccount().getEquityStatistics(),
                elapsedSeconds,
                runNumber.incrementAndGet(),
                tradingSimulator.getId(),
                null,
                Chronological.now()
        );
    }

    /**
     * Runs a backtest with the specified algorithm and market supplier factories.
     *
     * @param algorithmFactory      the factory responsible for creating algorithm instances
     * @param marketSupplierFactory the factory responsible for creating market data suppliers
     * @param algorithmName         the unique identifier for the algorithm being tested
     */
    public BacktestReport run(AlgorithmFactory<? extends Algorithm> algorithmFactory,
                    MarketSupplierFactory marketSupplierFactory,
                    String algorithmName) {
        return run(AlgorithmBacktestContext.builder()
                .algorithmFactory(algorithmFactory)
                .marketSupplierFactory(marketSupplierFactory)
                .algorithmName(algorithmName)
                .build());
    }
}
