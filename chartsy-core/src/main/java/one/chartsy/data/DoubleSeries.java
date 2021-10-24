package one.chartsy.data;

import one.chartsy.time.Timeline;

public interface DoubleSeries {

    Timeline getTimeline();

    int length();

    double get(int index);

    DoubleDataset values();
}
