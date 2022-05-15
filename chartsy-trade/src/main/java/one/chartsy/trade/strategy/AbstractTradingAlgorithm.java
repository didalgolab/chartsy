/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.strategy;

import one.chartsy.core.ThreadContext;
import one.chartsy.trade.Execution;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Objects;

public abstract class AbstractTradingAlgorithm implements TradingAlgorithm {

    private final Logger log = LogManager.getLogger(getClass());
    protected final TradingAlgorithmContext context;
    private final Map<String, ?> parameters;


    public AbstractTradingAlgorithm() {
        this(Configuration.unwrap(ThreadContext.current()));
    }

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
     * Gives the Logger instance associated with the trading algorithm.
     */
    public final Logger log() {
        return log;
    }

    @Override
    public void onInit(TradingAlgorithmContext runtime) {
        // empty implementation, may be provided by a subclass
    }

    @Override
    public void onAfterInit() {
        // empty implementation, may be provided by a subclass
    }

    @Override
    public void onExecution(Execution execution) {
        log().debug("Received Execution: {}", execution);
    }

    @Override
    public void onExit(ExitState state) {
        // empty implementation, may be provided by a subclass
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
