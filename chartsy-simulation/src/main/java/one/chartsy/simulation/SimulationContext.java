package one.chartsy.simulation;

import one.chartsy.data.Series;
import one.chartsy.trade.TradingStrategyContext;
import org.immutables.value.Value;

import java.util.Collection;

@Value.Immutable
public interface SimulationContext extends TradingStrategyContext {

    SimulationProperties properties();

    SimulationContext withDataSeries(Collection<? extends Series<?>> datasets);
}
