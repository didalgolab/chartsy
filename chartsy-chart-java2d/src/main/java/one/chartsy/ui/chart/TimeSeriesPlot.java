/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart;

import one.chartsy.data.DoubleDataset;
import one.chartsy.ui.chart.data.VisibleValues;

public interface TimeSeriesPlot extends Plot {

    DoubleDataset getTimeSeries();
    
    VisibleValues getVisibleData(ChartContext cf);
    
}
