/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.reports;

import one.chartsy.kernel.boot.FrontEnd;
import one.chartsy.simulation.SimulationResult;
import one.chartsy.time.TimeLock;
import one.chartsy.ui.tree.TreeViewControl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;

import java.time.Duration;

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

    private final TimeLock lock = new TimeLock(Duration.ofSeconds(1));

    @Override
    public void onApplicationEvent(PayloadApplicationEvent<? extends SimulationResult> event) {
        log.debug("Received: {}", event);

        var report = frontEnd.getApplicationContext().getBean(Report.class, event.getPayload());
        if (report == null)
            log.warn("No Report resolved for payload {}", event.getPayload().getClass().getName());
        else {
            log.debug("Resolved {}", report.getClass().getName());

            reports.addChild(new ReportHolder(report));
            if (lock.relock())
                viewControl.invalidateRoot();
        }
    }
}
