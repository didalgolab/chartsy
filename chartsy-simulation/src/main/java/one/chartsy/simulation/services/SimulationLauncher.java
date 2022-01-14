package one.chartsy.simulation.services;

import one.chartsy.core.AbstractSimpleLauncher;
import one.chartsy.core.LaunchConfiguration;
import one.chartsy.trade.Strategy;

public class SimulationLauncher extends AbstractSimpleLauncher {

    public SimulationLauncher() {
        super(Strategy.class);
    }

    @Override
    public <R> R launch(LaunchConfiguration<R> configuration) {
        return null;
    }
}
