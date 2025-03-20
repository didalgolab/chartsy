/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation.engine;

import one.chartsy.simulation.time.PlaybackClock;
import one.chartsy.trade.algorithm.Algorithm;
import one.chartsy.trade.algorithm.MarketSupplier;

/**
 * Factory responsible for creating instances of {@link MarketSupplier}, explicitly linked to
 * a provided {@link PlaybackClock}.
 * <p>
 * This factory encapsulates the instantiation logic required to generate {@code MarketSupplier} instances tailored
 * to different simulation scenarios or live trading environments. It abstracts the complexity of initializing various
 * data sources, enabling the seamless integration of historical playback, real-time feeds, or hybrid market simulations
 * into the broader trading algorithm execution framework.
 * <p>
 * By accepting a {@link PlaybackClock}, the created {@code MarketSupplier} aligns its event timestamps and message sequences
 * precisely with the clock’s simulated time, thereby ensuring temporal consistency and deterministic replayability.
 *
 * @see MarketSupplier
 * @see PlaybackClock
 * @see Algorithm
 * @see AlgorithmBacktestRunner
 */
@FunctionalInterface
public interface MarketSupplierFactory {

    /**
     * Creates and returns a new, fully-initialized instance of a {@link MarketSupplier}, integrated with the specified
     * {@link PlaybackClock}.
     * <p>
     * Implementations of this method are expected to instantiate a {@code MarketSupplier} pre-configured to synchronize
     * its message flow precisely with the provided {@code clock}. The resulting supplier is ready for immediate use,
     * pending an invocation of its {@link MarketSupplier#open()} method prior to polling market messages.
     *
     * @param clock the {@link PlaybackClock} that provides the simulated or real-time reference for timestamp alignment
     * @return a fully-configured {@link MarketSupplier} instance, ready to supply sequential market messages
     */
    MarketSupplier create(PlaybackClock clock);
}
