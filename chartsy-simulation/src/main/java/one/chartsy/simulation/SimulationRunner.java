package one.chartsy.simulation;

import one.chartsy.data.IndexedSymbolResourceData;

import java.util.Collection;

public interface SimulationRunner {

    SimulationResult run(Collection<? extends IndexedSymbolResourceData<?>> datasets, SimulationDriver strategy);
}
