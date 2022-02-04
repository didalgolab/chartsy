package one.chartsy.ui.simulation.reports;

import one.chartsy.simulation.SimulationResult;
import one.chartsy.ui.reports.Report;

public class ReportableSimulationResult implements Report {
    private final SimulationResult simulationResult;

    public ReportableSimulationResult(SimulationResult simulationResult) {
        this.simulationResult = simulationResult;
    }

    @Override
    public String getName() {
        return simulationResult.uid().toString();
    }
}
