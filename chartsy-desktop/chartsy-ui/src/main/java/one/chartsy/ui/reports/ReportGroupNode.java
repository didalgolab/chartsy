/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.reports;

import one.chartsy.ui.nodes.EntityNode;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

import javax.swing.*;
import java.io.IOException;

public class ReportGroupNode extends AbstractNode implements EntityNode<ReportHolder> {

    private static final String REPORT_GROUP_ICON_BASE = "one/chartsy/ui/reports/reports.png";

    private final ReportHolder report;
    private final ChildFactory<?> childFactory;

    protected ReportGroupNode(ReportHolder report) {
        this(report, new ReportChildFactory(report));
    }

    protected ReportGroupNode(ReportHolder report, ChildFactory<? extends ReportHolder> childFactory) {
        this(report, childFactory, Lookups.fixed(report, childFactory));
    }

    protected ReportGroupNode(ReportHolder report, ChildFactory<? extends ReportHolder> childFactory, Lookup lookup) {
        super(Children.create(childFactory, false), lookup);
        this.report = report;
        this.childFactory = childFactory;
        setName(report.getName());
        setIconBaseWithExtension(REPORT_GROUP_ICON_BASE);
    }

    @Override
    public ReportHolder getEntity() {
        return report;
    }

    @Override
    public Comparable<?> getEntityIdentifier() {
        return getEntity().getName();
    }

    @Override
    public ChildFactory<?> getChildFactory() {
        return childFactory;
    }

    @Override
    public Action[] getActions(boolean context) {
        return new Action[] {
        };
    }

    @Override
    public boolean canDestroy() {
        return true;
    }

    @Override
    public void destroy() throws IOException {
        super.destroy();
    }
}
