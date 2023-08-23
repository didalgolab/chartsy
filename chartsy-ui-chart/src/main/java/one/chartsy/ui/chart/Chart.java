/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import one.chartsy.core.Named;
import one.chartsy.data.CandleSeries;

import java.awt.Graphics2D;
import java.io.Serializable;

/**
 * Displays a symbol's price over time. A number of various chart types is
 * supported and implemented by subclasses, including the most popular OHLC,
 * candlestick and line charts.
 * 
 * @author Mariusz Bernacki
 */
public interface Chart extends Named, Serializable {
    
    /**
     * Gives the human-readable name describing this chart type implementation.
     * 
     * @return the chart type name
     */
    @Override
    String getName();
    
    /**
     * Gives the dataset compatible with this chart type.
     * 
     * @param dataset
     *            the original logical dataset as given by the data provider
     * @return the dataset to be plotted by this chart
     */
    default CandleSeries transformDataset(CandleSeries dataset) {
        return dataset;
    }
    
    /**
     * The chart drawing method. The method is responsible for drawing the
     * {@code Quotes} object on the given graphics context {@code g}.
     * 
     * @param g
     *            the graphics context used for drawing
     * @param cf
     *            the chart frame in which the chart is embedded
     * @param width
     *            the drawable chart area width
     * @param height
     *            the drawable chart area height
     */
    void paint(Graphics2D g, ChartContext cf, int width, int height);
    
}
