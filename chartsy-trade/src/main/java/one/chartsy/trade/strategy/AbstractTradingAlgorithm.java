/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.strategy;

import one.chartsy.When;
import one.chartsy.core.ThreadContext;
import one.chartsy.trade.Execution;
import one.chartsy.trade.MarketUniverseChangeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public abstract class AbstractTradingAlgorithm implements TradingAlgorithm {
    /** The unique identifier of the strategy. */
    private final UUID strategyUUID = UUID.randomUUID();

    private final Logger log = LogManager.getLogger(getClass());
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
        log.info("Algorithm {} initializing", getId());
    }

    @Override
    public void onAfterInit() {
        log.info("Algorithm {} initialized", getId());
    }

    @Override
    public void onExecution(Execution execution) {
        log().debug("Received Execution: {}", execution);
    }

    @Override
    public void onExit(ExitState state) {
        // empty implementation, may be provided by a subclass
    }

    @Override
    public void onTradingDayStart(LocalDate date) {
        log.debug("Algorithm's {} trading date {} started", getId(), date);
    }

    @Override
    public void onTradingDayEnd(LocalDate date) {
        log.debug("Algorithm's {} trading date {} ended", getId(), date);
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
            log().debug("Algorithm {} has removed from market universe: {}", getId(), change.getRemovedSymbols());
        if (change.hasAddedSymbols())
            log().debug("Algorithm {} has added to market universe: {}", getId(), change.getAddedSymbols());
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
