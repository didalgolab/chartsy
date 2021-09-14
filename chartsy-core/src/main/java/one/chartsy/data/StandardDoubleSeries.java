package one.chartsy.data;

import one.chartsy.time.Timeline;

public class StandardDoubleSeries implements DoubleSeries {

    private final Timeline timeline;
    private final DoubleDataset dataset;

    public StandardDoubleSeries(Timeline timeline, DoubleDataset dataset) {
        this.timeline = timeline;
        this.dataset = dataset;
    }

    @Override
    public Timeline getTimeline() {
        return timeline;
    }

    @Override
    public int length() {
        return dataset.length();
    }

    @Override
    public double get(int index) {
        return dataset.get(index);
    }

    @Override
    public DoubleDataset getDataset() {
        return dataset;
    }

    @Override
    public String toString() {
        return dataset.toString();
    }
}
