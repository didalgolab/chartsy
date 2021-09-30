/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart.plot;

import java.awt.Color;

import one.chartsy.data.DoubleDataset;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.TimeSeriesPlot;
import one.chartsy.ui.chart.data.VisibleValues;

public abstract class AbstractTimeSeriesPlot extends AbstractPlot implements TimeSeriesPlot {
    /** The time series associated with this plot. */
    protected final DoubleDataset timeSeries;
    
    
    /**
     * @return the timeSeries
     */
    @Override
    public DoubleDataset getTimeSeries() {
        return timeSeries;
    }
    
    protected AbstractTimeSeriesPlot(DoubleDataset timeSeries, Color primaryColor) {
        super(primaryColor);
        this.timeSeries = timeSeries;
    }
    
    @Override
    public VisibleValues getVisibleData(ChartContext cf) {
        if (timeSeries != null)
            return cf.getChartData().getVisible().getVisibleDataset(timeSeries);
        return null;
    }
}
