package one.chartsy.trade;

import one.chartsy.data.Series;
import one.chartsy.naming.SymbolIdentifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public interface StrategyConfig {

    SymbolIdentifier symbol();

    List<? extends Series<?>> dataSources();

    ConcurrentMap<String, ?> sharedVariables();

    Map<String, ?> properties();

    Account account();
}
