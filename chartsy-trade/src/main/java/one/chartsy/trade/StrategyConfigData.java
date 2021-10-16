package one.chartsy.trade;

import one.chartsy.data.Series;
import one.chartsy.naming.SymbolIdentifier;

import java.util.List;
import java.util.Map;

public record StrategyConfigData(
        SymbolIdentifier symbol,
        List<Series<?>> dataSources,
        Map<String, ?> properties
) implements StrategyConfig {

}
