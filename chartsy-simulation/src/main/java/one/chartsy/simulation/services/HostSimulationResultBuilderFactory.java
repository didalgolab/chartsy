package one.chartsy.simulation.services;

import one.chartsy.simulation.SimulationResult;
import org.openide.util.lookup.ServiceProvider;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@ServiceProvider(service = SimulationResultBuilderFactory.class)
public class HostSimulationResultBuilderFactory implements SimulationResultBuilderFactory {

    @Override
    public SimulationResult.Builder create(Map<String, ?> props) {
        return new SimulationResult.Builder()
                .startTime(LocalDateTime.MAX)
                .endTime(LocalDateTime.MAX)
                .testDuration(Duration.ZERO)
                .estimatedDataPointCount(0)
                .remainingOrderCount(0);
    }
}
