package one.chartsy.trade;

import one.chartsy.data.Series;
import one.chartsy.naming.SymbolIdentifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public record StrategyConfigData(
        SymbolIdentifier symbol,
        List<Series<?>> dataSources,
        ConcurrentMap<String, Object> sharedVariables,
        Map<String, ?> properties,
        Account account
) implements StrategyConfig {

}
