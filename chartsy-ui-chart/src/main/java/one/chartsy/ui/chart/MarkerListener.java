/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

/**
 * The listener interface for receiving notifications when a marker line has been
 * positioned on the chart.
 * 
 * @author Mariusz Bernacki
 * 
 */
@FunctionalInterface
public interface MarkerListener {
    
    /**
     * The marker notification indicates a marker, which has been embedded or
     * programmatically inserted in the chart. The event is also generated when the
     * marker position index has been manually or programmatically changed.
     *
     * @param e
     *            the marker event
     */
    void onMarker(MarkerEvent e);
}
