package one.chartsy.trade;

import one.chartsy.data.Series;
import one.chartsy.naming.SymbolIdentifier;

import java.util.List;
import java.util.Map;

public interface StrategyConfig {

    SymbolIdentifier symbol();

    List<? extends Series<?>> dataSources();

    Map<String, ?> properties();

    Account account();
}
