/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.plot;

import one.chartsy.data.DoubleDataset;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.Plot;
import one.chartsy.ui.chart.data.VisibleValues;

public interface TimeSeriesPlot extends Plot {

    DoubleDataset getTimeSeries();
    
    VisibleValues getVisibleData(ChartContext cf);
    
}
