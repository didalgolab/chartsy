/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import one.chartsy.ui.chart.ChartContext;

import javax.swing.BorderFactory;
import javax.swing.JLayeredPane;
import java.awt.BorderLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class MainPanel extends JLayeredPane {

    private final ChartContext chartFrame;
    private final ChartStackPanel sPane;

    public MainPanel(ChartContext frame) {
        chartFrame = frame;
        sPane = new ChartStackPanel(chartFrame);

        setOpaque(true);
        setBackground(chartFrame.getChartProperties().getBackgroundColor());
        setBorder(BorderFactory.createEmptyBorder());
        setLayout(new BorderLayout());
        add(sPane, BorderLayout.CENTER);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                refreshCharts();
            }
        });

        putClientProperty("print.printable", Boolean.TRUE);
        putClientProperty("print.name", "");
    }

    public ChartStackPanel getStackPanel() {
        return sPane;
    }

    public void refreshCharts() {
        if (!chartFrame.getChartData().hasDataset())
            return;

        chartFrame.getChartData().calculate(chartFrame);
        chartFrame.getChartData().calculateRange(chartFrame, sPane.getChartPanel().getOverlays());
        sPane.refreshPanels();
        repaint();
    }

    public ChartPanel getChartPanel() {
        return getStackPanel().getChartPanel();
    }

    public void deselectAll() {
        getChartPanel().getAnnotationPanel().deselectAll();
        for (IndicatorPanel ip : getStackPanel().getIndicatorPanels())
            ip.getAnnotationPanel().deselectAll();
    }
}
