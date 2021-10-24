package one.chartsy.data;

import one.chartsy.time.Timeline;

public interface TimeSeriesAlike {

    Timeline getTimeline();

    int length();
}
