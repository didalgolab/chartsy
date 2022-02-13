package one.chartsy.simulation;

import one.chartsy.data.Series;
import one.chartsy.trade.strategy.TradingAgentRuntime;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
public interface SimulationContext extends TradingAgentRuntime {

    SimulationContext withDataSeries(List<? extends Series<?>> datasets);
}
