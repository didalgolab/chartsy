package one.chartsy.simulation;

import one.chartsy.data.Series;
import one.chartsy.trade.TradingStrategyContext;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
public interface SimulationContext extends TradingStrategyContext {

    SimulationProperties properties();

    SimulationContext withDataSeries(List<? extends Series<?>> datasets);
}
