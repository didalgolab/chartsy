/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui;

import one.chartsy.ui.chart.ChartData;
import one.chartsy.ui.chart.ChartFrame;
import one.chartsy.ui.chart.ChartTemplate;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;

import java.awt.*;

/**
 * The main chart display component.
 *
 * @author Mariusz Bernacki
 */
@TopComponent.Description(preferredID = "Chart", iconBase = "one/chartsy/ui/resources/bar-chart.png", persistenceType = TopComponent.PERSISTENCE_NEVER)
public class ChartTopComponent extends TopComponent implements TopComponent.Cloneable {

    private final ChartFrame chart;

    public ChartTopComponent(ChartFrame chart) {
        setLayout(new BorderLayout());
        setName(NbBundle.getMessage(ChartTopComponent.class, "ChartTopComponent.name"));
        setOpaque(false);
        this.chart = chart;

        add(chart);
        addListeners(chart);
    }

    private void addListeners(ChartFrame chart) {
        chart.addPropertyChangeListener("name", evt -> setName(evt.getNewValue().toString()));
        setName(chart.getName());
    }

    public ChartData getChartData() {
        return chart.getChartData();
    }

    public ChartTemplate getChartTemplate() {
        return chart.getChartTemplate();
    }

    @Override
    public TopComponent cloneComponent() {
        var currChartData = getChartData();
        var chartData = new ChartData();
        chartData.setSymbol(currChartData.getSymbol());
        chartData.setDataProvider(currChartData.getDataProvider());
        chartData.setTimeFrame(currChartData.getTimeFrame());
        //chartData.setChart(ChartManager.getDefault().getChart(currChartData.getChart().getName()));
        chartData.setChart(currChartData.getChart());

        var chartFrame = new ChartFrame();
        chartFrame.setChartData(chartData);
        chartFrame.setChartTemplate(getChartTemplate());

        return new ChartTopComponent(chartFrame);
    }
}
