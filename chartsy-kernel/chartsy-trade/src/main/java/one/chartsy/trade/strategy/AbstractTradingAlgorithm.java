/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.strategy;

import one.chartsy.When;
import one.chartsy.base.ThreadContext;
import one.chartsy.trade.Execution;
import one.chartsy.trade.MarketUniverseChangeEvent;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public abstract class AbstractTradingAlgorithm implements TradingAlgorithm {
    /** The unique identifier of the strategy. */
    private final UUID strategyUUID = UUID.randomUUID();

    private final Logger log = System.getLogger(getClass().getName());
    protected final TradingAlgorithmContext context;
    private final Map<String, ?> parameters;


    public AbstractTradingAlgorithm(TradingAlgorithmContext context) {
        this(context, Map.of());
    }

    public AbstractTradingAlgorithm(TradingAlgorithmContext context, Map<String, ?> parameters) {
        this.context = Objects.requireNonNull(context, "context");
        this.parameters = Map.copyOf(parameters);
    }

    private AbstractTradingAlgorithm(Configuration config) {
        this(config.context(), config.parameters());
    }

    /**
     * Gives a unique id of the trading algorithm.
     *
     * @return the algorithm's ID, unique within the scope of the parent TradingSimulator
     */
    public String getId() {
        return context.name();
    }

    /**
     * Gives the unique UUID identifier of the strategy.
     *
     * @return the strategy UUID
     */
    public final UUID getStrategyUUID() {
        return strategyUUID;
    }

    /**
     * Gives the Logger instance associated with the trading algorithm.
     */
    public final Logger log() {
        return log;
    }

    @Override
    public void onInit(TradingAlgorithmContext runtime) {
        log.log(Level.DEBUG, "Algorithm {0} initializing", getId());
    }

    @Override
    public void onAfterInit() {
        log.log(Level.DEBUG, "Algorithm {0} initialized", getId());
    }

    @Override
    public void onExecution(Execution execution) {
        log().log(Level.DEBUG, "Received Execution: {0}", execution);
    }

    @Override
    public void onExit(ExitState state) {
        // empty implementation, may be provided by a subclass
    }

    @Override
    public void onTradingDayStart(LocalDate date) {
        log.log(Level.DEBUG, "Algorithm's {0} trading date {1} started", getId(), date);
    }

    @Override
    public void onTradingDayEnd(LocalDate date) {
        log.log(Level.DEBUG, "Algorithm's {0} trading date {1} ended", getId(), date);
    }

    @Override
    public void doFirst(When when) {
        // no implementation, may be overridden by a subclass
    }

    @Override
    public void doLast(When when) {
        // no implementation, may be overridden by a subclass
    }

    @Override
    public void onMarketUniverseChange(MarketUniverseChangeEvent change) {
        if (change.hasRemovedSymbols())
            log().log(Level.DEBUG, "Algorithm {0} has removed from market universe: {1}", getId(), change.getRemovedSymbols());
        if (change.hasAddedSymbols())
            log().log(Level.DEBUG, "Algorithm {0} has added to market universe: {1}", getId(), change.getAddedSymbols());
    }

    private record Configuration(
            TradingAlgorithmContext context,
            Map<String, ?> parameters
    ) {
        private static final String PAYLOAD_KEY = "config";

        private ThreadContext wrap() {
            return ThreadContext.of(Map.of(PAYLOAD_KEY, this));
        }

        private static Configuration unwrap(ThreadContext ctx) {
            Object payload = ctx.getVars().get(PAYLOAD_KEY);
            if (!(payload instanceof Configuration config))
                throw new IllegalArgumentException("AbstractTradingAlgorithm.Configuration is missing");

            return config;
        }
    }
}
