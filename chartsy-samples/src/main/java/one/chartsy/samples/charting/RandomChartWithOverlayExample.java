/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.charting;

import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.ui.chart.indicators.SforaWidth;
import one.chartsy.ui.chart.overlays.Sfora;
import one.chartsy.data.CandleSeries;
import one.chartsy.random.RandomWalk;
import one.chartsy.ui.chart.ChartData;
import one.chartsy.ui.chart.ChartFrame;
import one.chartsy.ui.chart.ChartTemplate;
import one.chartsy.ui.chart.type.CandlestickChart;

import javax.swing.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public class RandomChartWithOverlayExample {

    public static void main(String[] args) throws IOException, InterruptedException, InvocationTargetException {
        CandleSeries series = RandomWalk.candleSeries(1000, SymbolResource.of("RANDOM", TimeFrame.Period.DAILY));

        SwingUtilities.invokeAndWait(() -> {
            ChartData chartData = new ChartData();
            chartData.setChart(new CandlestickChart());
            chartData.setDataset(series);

            ChartTemplate template = new ChartTemplate("Default");
            template.addOverlay(new Sfora());
            template.addIndicator(new SforaWidth());

            ChartFrame chartFrame = new ChartFrame();
            chartFrame.setChartData(chartData);
            chartFrame.setChartTemplate(template);

            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            JFrame frame = new JFrame("Chart");
            frame.getContentPane().add(chartFrame);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.pack();
            frame.setVisible(true);
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        });

        System.out.println(series.length());
        //System.out.println("TIME INFO: " + (System.nanoTime() - startTime)/1000_000L + " ms, " + list.size());
        //System.out.println(ClassLayout.parseInstance(list.get(0)).toPrintable());
        //System.in.read();
    }
}
