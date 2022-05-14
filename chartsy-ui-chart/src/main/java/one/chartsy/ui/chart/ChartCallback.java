/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import one.chartsy.SymbolIdentity;

/**
 * Used to run some code when the chart with the particular symbol is shown.
 * 
 * The implementations of this interface can be handed to a {@link ChartCallbackRegistry} and are notified each
 * time a chart with a particular symbol is opened.
 * 
 * @author Mariusz Bernacki
 *
 */
public interface ChartCallback<T> {
    
    /**
     * Called when the symbol chart is shown.
     * 
     * @param chart
     *            the chart frame which was shown
     * @param tag
     *            a user object provided through
     *            {@link ChartCallbackRegistry#addChartCallback(SymbolIdentity, Object, ChartCallback)}
     *            method call.
     */
    void onChart(ChartFrame chart, T tag);
    
    
    /**
     * Functional equivalent of {@code ChartCallback<Void>} type. Provided to
     * simplify chart callback constructs using lambda expressions.
     * 
     * @author Mariusz Bernacki
     */
    @FunctionalInterface
    interface TagFree extends ChartCallback<Void> {
        
        @Override
        default void onChart(ChartFrame chart, Void tag) {
            onChart(chart);
        }
        
        /**
         * Called when the symbol chart is shown.
         * 
         * @param chart
         *            the chart frame which was shown
         */
        void onChart(ChartFrame chart);
        
    }
}
