/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.charting;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DataQuery;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.ui.chart.ChartData;
import one.chartsy.ui.chart.ChartFrame;
import one.chartsy.ui.chart.ChartTemplate;
import one.chartsy.ui.chart.StudyRegistry;
import one.chartsy.ui.chart.type.CandlestickChart;

import javax.swing.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Objects;

public class StooqChartWithOverlayExample {

    public static void main(String[] args) throws IOException, InterruptedException, InvocationTargetException {
        FlatFileDataProvider dataProvider = FlatFileFormat.STOOQ
                .newDataProvider(Path.of("C:/Downloads/d_pl_txt(3).zip"));

        DataQuery<Candle> query = DataQuery.resource(
                SymbolResource.of("BDX", TimeFrame.Period.DAILY))
                .limit(500)
                .build();

        CandleSeries series = dataProvider.queryForCandles(query)
                .collectSortedList()
                .as(CandleSeries.of(query.resource()));

        SwingUtilities.invokeAndWait(() -> {
            ChartData chartData = new ChartData();
            chartData.setChart(new CandlestickChart());
            //chartData.setDataset(series);
            chartData.setDataProvider(dataProvider);
            chartData.setSymbol(series.getResource().symbol());
            chartData.setTimeFrame(series.getTimeFrame());

            ChartTemplate template = new ChartTemplate("Default");
            template.addOverlay(Objects.requireNonNull(StudyRegistry.getDefault().getOverlay("Sfora")));
            template.addIndicator(Objects.requireNonNull(StudyRegistry.getDefault().getIndicator("Sfora, Width")));

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
