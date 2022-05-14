/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.charting;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.SimpleCandle;
import one.chartsy.random.RandomWalk;
import one.chartsy.ui.chart.ChartData;
import one.chartsy.ui.chart.ChartFrame;
import one.chartsy.ui.chart.type.CandlestickChart;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class RandomWalkChart {

    public static void main(String[] args) throws IOException, InterruptedException, InvocationTargetException {
        SymbolResource<Candle> symbol = SymbolResource.of("RANDOM", TimeFrame.Period.DAILY);
        List<Candle> list = RandomWalk.candles()
                .limit(1000)
                .toList();
        list = new ArrayList<>(list);
        Collections.reverse(list);
        CandleSeries series = CandleSeries.of(symbol, list);

        SwingUtilities.invokeAndWait(() -> {
            ChartData chartData = new ChartData();
            chartData.setChart(new CandlestickChart());
            chartData.setDataset(series);

            ChartFrame chartFrame = new ChartFrame();
            chartFrame.setChartData(chartData);

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
