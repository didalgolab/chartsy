/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation.engine;

import one.chartsy.data.stream.ArrayBlockingQueueMessageBuffer;
import one.chartsy.simulation.time.PlaybackClock;
import one.chartsy.trade.algorithm.Algorithm;
import one.chartsy.trade.algorithm.AlgorithmFactory;
import one.chartsy.trade.algorithm.AlgorithmWorker;
import one.chartsy.trade.algorithm.StandardAlgorithmContext;

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

    /**
     * Runs the specified trading algorithm backtest using provided factories for the algorithm and market supplier.
     * <p>
     * This method orchestrates the full lifecycle of a backtest session - from initialization of the execution environment
     * and algorithm instantiation to market data provisioning and sequential event processing - until all events are
     * consumed or an algorithm-induced shutdown occurs.
     *
     * @param configuration the algorithm and the backtest run configurations
     */
    public void run(AlgorithmBacktestContext configuration) {
        var startTime = System.nanoTime();
        var clock = new PlaybackClock();
        var queue = new ArrayBlockingQueueMessageBuffer<>(1024);
        var context = new StandardAlgorithmContext(configuration.algorithmName(), clock, queue);
        var algorithm = configuration.algorithmFactory().create(context);
        var worker = new AlgorithmWorker(algorithm, queue, configuration.marketSupplierFactory().create(clock));

        try {
            worker.onOpen();
            while (worker.doWork() > 0)
                if (context.isShutdown())
                    break;

        } finally {
            worker.onClose();
        }

        long millis = (System.nanoTime() - startTime) / 1_000_000;
        System.out.println("Elapsed time [sec]: " + (millis / 1000.0));
    }

    /**
     * Runs a backtest with the specified algorithm and market supplier factories.
     *
     * @param algorithmFactory      the factory responsible for creating algorithm instances
     * @param marketSupplierFactory the factory responsible for creating market data suppliers
     * @param algorithmName         the unique identifier for the algorithm being tested
     */
    public void run(AlgorithmFactory<? extends Algorithm> algorithmFactory,
                    MarketSupplierFactory marketSupplierFactory,
                    String algorithmName) {
        run(AlgorithmBacktestContext.builder()
                .algorithmFactory(algorithmFactory)
                .marketSupplierFactory(marketSupplierFactory)
                .algorithmName(algorithmName)
                .build());
    }
}
