/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.trade;

import one.chartsy.core.ThreadContext;
import one.chartsy.time.Chronological;

import one.chartsy.trade.strategy.TradingAlgorithm;
import one.chartsy.trade.strategy.TradingAlgorithmContext;
import one.chartsy.trade.strategy.TradingAlgorithmFactory;
import org.apache.commons.lang3.reflect.TypeUtils;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Map;

public class StrategyInstantiator {

    private final TradingAlgorithmContext context;

    public StrategyInstantiator(TradingAlgorithmContext context) {
        this.context = context;
    }

    public TradingAlgorithm newInstance(TradingAlgorithmFactory provider, StrategyConfigData config) {
        return ThreadContext.of(Map.of("config", config)).execute(() -> provider.create(context));
    }

    static Class<?> probeDataType(Class<? extends Strategy<?>> strategyClass) {
        Map<TypeVariable<?>, Type> typeArguments = TypeUtils.getTypeArguments(strategyClass, Strategy.class);

        Type type = typeArguments.get(Strategy.class.getTypeParameters()[0]);
        if (type instanceof Class)
            return (Class<?>) type;

        if (type instanceof TypeVariable typeVar)
            for (Type bound : typeVar.getBounds())
                if (bound instanceof Class && Chronological.class.isAssignableFrom((Class<?>) bound))
                    return (Class<?>) bound;

        return Chronological.class;
    }
}
