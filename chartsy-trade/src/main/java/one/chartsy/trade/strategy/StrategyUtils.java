/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.strategy;

import one.chartsy.time.Chronological;

import org.apache.commons.lang3.reflect.TypeUtils;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Map;

public class StrategyUtils {

    static Class<?> probeDataType(Class<? extends Strategy<?>> strategyClass) {
        Map<TypeVariable<?>, Type> typeArguments = TypeUtils.getTypeArguments(strategyClass, Strategy.class);

        Type type = typeArguments.get(Strategy.class.getTypeParameters()[0]);
        if (type instanceof Class)
            return (Class<?>) type;

        if (type instanceof TypeVariable<?> typeVar)
            for (Type bound : typeVar.getBounds())
                if (bound instanceof Class<?> classBound && Chronological.class.isAssignableFrom(classBound))
                    return classBound;

        return Chronological.class;
    }
}
