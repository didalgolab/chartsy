/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
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
