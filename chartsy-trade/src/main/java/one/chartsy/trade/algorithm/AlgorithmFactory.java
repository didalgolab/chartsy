/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm;

/**
 * A factory class responsible for creating instances of {@link Algorithm} 
 * tailored to a specific trading strategy. The factory leverages the provided 
 * {@link AlgorithmContext} to configure and instantiate an algorithm that is 
 * ready for back-testing or live execution.
 *
 * @param <T> the type of {@link Algorithm} that this factory produces
 */
public interface AlgorithmFactory<T extends Algorithm> {

    /**
     * Creates and configures an instance of an {@link Algorithm} using the 
     * specified {@link AlgorithmContext}.
     *
     * <p>The created algorithm is fully prepared for execution, having been
     * initialized with the necessary market data, parameters, and state
     * information derived from the provided context. This method ensures that
     * the algorithm is appropriately set up for subsequent operations, whether
     * for back-testing or live trading.
     *
     * @param context the {@link AlgorithmContext} that provides the necessary 
     *                information to configure the algorithm
     * @return a fully-configured instance of {@link Algorithm} ready for 
     *         execution
     */
    T create(AlgorithmContext context);
}
