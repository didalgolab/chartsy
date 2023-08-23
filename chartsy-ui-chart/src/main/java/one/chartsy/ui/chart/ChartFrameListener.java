/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import java.util.EventListener;

import one.chartsy.SymbolIdentity;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;

public interface ChartFrameListener extends EventListener, java.io.Serializable {
    
    default void symbolChanged(SymbolIdentity newSymbol) {}
    
    default void timeFrameChanged(TimeFrame newTimeFrame) {}
    
    default void chartChanged(Chart newChart) {}
    
    default void datasetChanged(CandleSeries quotes) {}
    
    default void indicatorAdded(Indicator indicator) {}
    
    default void indicatorRemoved(Indicator indicator) {}
    
    default void overlayAdded(Overlay overlay) {}
    
    default void overlayRemoved(Overlay overlay) {}
    
}
