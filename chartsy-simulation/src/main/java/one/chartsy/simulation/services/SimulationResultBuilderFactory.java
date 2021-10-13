package one.chartsy.simulation.services;

import one.chartsy.simulation.SimulationResult;

import java.util.Map;

@FunctionalInterface
public interface SimulationResultBuilderFactory {

    SimulationResult.Builder create(Map<String,?> props);
}
