package one.chartsy.trade;

import one.chartsy.core.ThreadContext;
import one.chartsy.time.Chronological;

import org.apache.commons.lang3.reflect.TypeUtils;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Map;

public class StrategyInitializer {

    public TradingStrategy newInstance(TradingStrategyProvider provider, StrategyConfigData config) {
        return ThreadContext.of(Map.of("config", config)).execute(provider::createInstance);
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
