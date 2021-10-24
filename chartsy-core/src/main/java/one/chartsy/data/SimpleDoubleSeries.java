package one.chartsy.data;

import one.chartsy.time.Timeline;

public class SimpleDoubleSeries implements DoubleSeries {

    private final Timeline timeline;
    private final DoubleDataset values;

    public SimpleDoubleSeries(Timeline timeline, DoubleDataset values) {
        this.timeline = timeline;
        this.values = values;
    }

    @Override
    public Timeline getTimeline() {
        return timeline;
    }

    @Override
    public int length() {
        return values.length();
    }

    @Override
    public double get(int index) {
        return values.get(index);
    }

    @Override
    public DoubleDataset values() {
        return values;
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
