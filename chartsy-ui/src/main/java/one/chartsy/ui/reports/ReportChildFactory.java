/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.reports;

import one.chartsy.core.Refreshable;
import one.chartsy.time.TimeLock;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;

import java.time.Duration;
import java.util.List;

public class ReportChildFactory extends ChildFactory<ReportHolder> implements Refreshable {

    private final ReportHolder holder;

    public ReportChildFactory(ReportHolder holder) {
        this.holder = holder;
    }

    @Override
    protected boolean createKeys(List<ReportHolder> toPopulate) {
        toPopulate.addAll(holder.getChildren());
        var report = holder.getReport();
        if (report.isPresent())
            toPopulate.add(new ReportHolder(report.get()));
        return true;
    }
    
    @Override
    protected Node createNodeForKey(ReportHolder report) {
        return new ReportGroupNode(report, new ReportChildFactory(report));
    }

    @Override
    public void refresh() {
        super.refresh(false);
    }

    public void addSubReport(ReportHolder report) {
        holder.addChild(report);
        super.refresh(false);
    }
}
