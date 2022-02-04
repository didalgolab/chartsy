package one.chartsy.ui.reports;

import one.chartsy.frontend.FrontEnd;
import one.chartsy.simulation.SimulationResult;
import one.chartsy.ui.tree.TreeViewControl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;

public class ReportViewListener implements ApplicationListener<PayloadApplicationEvent<? extends SimulationResult>> {
    protected final Logger log = LogManager.getLogger(getClass());
    protected final FrontEnd frontEnd;
    protected final ReportHolder reports;
    protected final TreeViewControl viewControl;

    public ReportViewListener(FrontEnd frontEnd, ReportHolder reports, TreeViewControl viewControl) {
        this.frontEnd = frontEnd;
        this.reports = reports;
        this.viewControl = viewControl;
    }

    @Override
    public void onApplicationEvent(PayloadApplicationEvent<? extends SimulationResult> event) {
        log.debug("Received: {}", event);

        var report = frontEnd.getApplicationContext().getBean(Report.class, event.getPayload());
        if (report == null)
            log.warn("No Report resolved for payload {}", event.getPayload().getClass().getName());
        else {
            log.debug("Resolved {}", report.getClass().getName());

            reports.addChild(new ReportHolder(report));
            viewControl.invalidateRoot();
        }
    }
}
