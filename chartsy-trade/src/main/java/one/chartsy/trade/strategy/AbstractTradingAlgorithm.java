/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.trade.strategy;

import one.chartsy.core.ThreadContext;

import java.util.Map;
import java.util.Objects;

public class AbstractTradingAlgorithm {

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
