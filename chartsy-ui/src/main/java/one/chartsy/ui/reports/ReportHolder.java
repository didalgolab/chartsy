/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.reports;

import java.util.*;

public class ReportHolder {

    private final String name;
    private final Report report;
    private final List<ReportHolder> children = Collections.synchronizedList(new ArrayList<>());


    public ReportHolder(String name) {
        this(name, null);
    }

    public ReportHolder(Report report) {
        this(report.getName(), report);
    }

    protected ReportHolder(String name, Report report) {
        this.name = Objects.requireNonNull(name, "name");
        this.report = report;
    }

    public String getName() {
        return name;
    }

    public Optional<Report> getReport() {
        return Optional.ofNullable(report);
    }

    public List<ReportHolder> getChildren() {
        return children;
    }

    public void addChild(ReportHolder child) {
        children.add(child);
    }
}
