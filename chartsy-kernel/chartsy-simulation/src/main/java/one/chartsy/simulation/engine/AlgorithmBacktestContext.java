/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation.engine;

import one.chartsy.trade.algorithm.Algorithm;
import one.chartsy.trade.algorithm.AlgorithmFactory;
import one.chartsy.trade.algorithm.MarketSupplier;

import java.util.Objects;

/**
 * Encapsulates the configuration and dependencies required to execute an algorithm backtest.
 * <p>
 * This context class provides all necessary parameters for running a specific algorithm within
 * the backtesting environment, including the identification of the algorithm, the factory responsible
 * for creating algorithm instances, and the means of supplying market data through a {@link MarketSupplierFactory}.
 * Instances of this context enable deterministic, reproducible, and configurable backtest executions.
 *
 * @see MarketSupplierFactory
 * @see Algorithm
 * @see AlgorithmFactory
 * @see AlgorithmBacktestRunner
 */
public record AlgorithmBacktestContext(
        String algorithmName,
        AlgorithmFactory<? extends Algorithm> algorithmFactory,
        MarketSupplierFactory marketSupplierFactory
) {

    /**
     * Constructs an {@link AlgorithmBacktestContext} instance with validation.
     *
     * @param algorithmName          the unique identifier for the algorithm being backtested; must be non-null and non-empty
     * @param algorithmFactory       the factory to instantiate algorithm implementations; must be non-null
     * @param marketSupplierFactory  the factory to produce {@link MarketSupplier} instances; must be non-null
     */
    public AlgorithmBacktestContext {
        Objects.requireNonNull(algorithmName, "algorithmName must not be null");
        Objects.requireNonNull(algorithmFactory, "algorithmFactory must not be null");
        Objects.requireNonNull(marketSupplierFactory, "marketSupplierFactory must not be null");
        if (algorithmName.isBlank())
            throw new IllegalArgumentException("algorithmName must not be blank");
    }

    /**
     * Creates a new {@link Builder} for fluently constructing an {@link AlgorithmBacktestContext}.
     *
     * @return a new instance of {@code Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for constructing instances of {@link AlgorithmBacktestContext}.
     * <p>
     * The builder facilitates step-by-step construction and validation of backtest context instances,
     * improving readability, configurability, and maintainability of test setups.
     */
    public static class Builder {
        private String algorithmName = "Custom Algorithm";
        private AlgorithmFactory<? extends Algorithm> algorithmFactory;
        private MarketSupplierFactory marketSupplierFactory;

        /**
         * Sets the algorithm's unique identifier.
         *
         * @param algorithmName the name identifying the algorithm; must not be null or blank
         * @return the current {@code Builder} instance for method chaining
         */
        public Builder algorithmName(String algorithmName) {
            this.algorithmName = algorithmName;
            return this;
        }

        /**
         * Sets the {@link AlgorithmFactory} responsible for instantiating algorithms.
         *
         * @param algorithmFactory the factory for algorithm instances; must not be null
         * @return the current {@code Builder} instance for method chaining
         */
        public Builder algorithmFactory(AlgorithmFactory<? extends Algorithm> algorithmFactory) {
            this.algorithmFactory = algorithmFactory;
            return this;
        }

        /**
         * Sets the {@link MarketSupplierFactory} responsible for providing market data suppliers.
         *
         * @param marketSupplierFactory the factory to create market suppliers; must not be null
         * @return the current {@code Builder} instance for method chaining
         */
        public Builder marketSupplierFactory(MarketSupplierFactory marketSupplierFactory) {
            this.marketSupplierFactory = marketSupplierFactory;
            return this;
        }

        /**
         * Constructs a validated {@link AlgorithmBacktestContext} instance from the provided values.
         *
         * @return a fully-initialized {@link AlgorithmBacktestContext} ready for use in backtesting
         * @throws NullPointerException     if any required properties are unset
         * @throws IllegalArgumentException if any property validation fails
         */
        public AlgorithmBacktestContext build() {
            return new AlgorithmBacktestContext(algorithmName, algorithmFactory, marketSupplierFactory);
        }
    }
}
