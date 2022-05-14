/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

/**
 * This interface lets you specify the rendering style that must be used to display a given data point.
 */
public interface DataRenderingHint {
    
    /**
     * Returns the rendering style that must be used to display the specified point.
     * 
     * @param epochMicros
     * @param defaultStyle
     * @return The rendering style that must be used to display the point. A
     *         {@code null} value indicates that the point should not be displayed.
     */
    PlotStyle getStyle(long epochMicros, PlotStyle defaultStyle);
    
}
