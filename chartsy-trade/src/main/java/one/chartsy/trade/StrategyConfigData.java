package one.chartsy.trade;

import one.chartsy.data.Series;
import one.chartsy.naming.SymbolIdentifier;
import org.openide.util.Lookup;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public record StrategyConfigData(
        Lookup lookup,
        SymbolIdentifier symbol,
        List<Series<?>> dataSources,
        ConcurrentMap<String, Object> sharedVariables,
        Map<String, ?> properties,
        Account account
) implements StrategyConfig {

}
