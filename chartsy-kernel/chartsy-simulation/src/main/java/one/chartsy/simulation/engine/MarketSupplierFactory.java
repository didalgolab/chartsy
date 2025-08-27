/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation.engine;

import one.chartsy.trade.algorithm.Algorithm;
import one.chartsy.trade.algorithm.MarketSupplier;

/**
 * Factory responsible for creating instances of {@link MarketSupplier}.
 * <p>
 * This factory encapsulates the instantiation logic required to generate {@code MarketSupplier} instances tailored
 * to different simulation scenarios or live trading environments. It abstracts the complexity of initializing various
 * data sources, enabling the seamless integration of historical playback, real-time feeds, or hybrid market simulations
 * into the broader trading algorithm execution framework.
 *
 * @see MarketSupplier
 * @see Algorithm
 * @see AlgorithmBacktestRunner
 */
@FunctionalInterface
public interface MarketSupplierFactory {

    /**
     * Creates and returns a new, fully-initialized instance of a {@link MarketSupplier}.
     * <p>
     * Implementations of this method are expected to instantiate a {@code MarketSupplier}.
     * The resulting supplier is ready for immediate use, pending an invocation of its
     * {@link MarketSupplier#open()} method prior to polling market messages.
     *
     * @return a fully-configured {@link MarketSupplier} instance, ready to supply sequential market messages
     */
    MarketSupplier create();
}
