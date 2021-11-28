/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart.axis;

import java.util.Locale;

/**
 * Lists axis measurement marks and labels.
 * 
 * @author Mariusz Bernacki
 *
 */
public interface AxisScale {
    
    /**
     * Returns a position of the i-th scale's label in the mappers mapping range.
     * 
     * @param i
     *            the label index
     * @return the axis position of the mark in double coordinates
     */
    double mapMark(int i);
    
    /**
     * Returns the axis mark count
     * 
     * @return the label count
     */
    int getMarkCount();
    
    /**
     * Returns the appropriate label for the axis mark.
     * 
     * @param i
     *            the label index
     * @return the label text
     */
    String getLabelAt(int i);
    
    /**
     * Returns the appropriate label for the axis mark formatted according to
     * the given Locale.
     * 
     * @param i
     *            the label index
     * @param locale
     *            the locale
     * @return the label text
     */
    String getLabelAt(int i, Locale locale);
    
    /**
     * Returns a sub-scale for the scale. The sub-scale handles the same axis
     * interval, but is finer-grained than the parent scale.
     * 
     * @return sub-scale or {@code null} if no sub-scale exists
     */
    AxisScale getSubScale();
    
}
