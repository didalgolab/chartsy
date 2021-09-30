package one.chartsy.ui.chart;

import one.chartsy.ui.chart.components.ChartStackPanel;

import java.util.EventObject;

/**
 * The MarkerEvent is emitted from the chart when the marker line is enabled and
 * its position has been changed.
 * 
 * @author Mariusz Bernacki
 * 
 */
public class MarkerEvent extends EventObject {
    /** The chart which generated the event. */
    private final ChartStackPanel chart;
    /** The marker position index. */
    private final int index;
    
    
    /**
     * Constructs a new chart marker event.
     *
     * @param chart the chart which generated the event
     */
    public MarkerEvent(ChartStackPanel chart, int index) {
        super(chart);
        this.chart = chart;
        this.index = index;
    }
    
    /**
     * Returns the chart which generated the event.
     *
     * @return the chart
     */
    public ChartStackPanel getChart() {
        return chart;
    }
    
    /**
     * Returns the marker position index.
     * 
     * @return the marker index
     */
    public int getIndex() {
        return index;
    }
}
