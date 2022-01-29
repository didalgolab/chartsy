package one.chartsy.ui.reports;

import one.chartsy.simulation.SimulationResult;
import one.chartsy.ui.tree.TreeViewControl;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;

public class ReportViewListener implements ApplicationListener<PayloadApplicationEvent<SimulationResult>> {

    private final TreeViewControl viewControl;

    public ReportViewListener(TreeViewControl viewControl) {
        this.viewControl = viewControl;
    }

    @Override
    public void onApplicationEvent(PayloadApplicationEvent<SimulationResult> event) {
        // TODO
        throw new UnsupportedOperationException();
    }
}
