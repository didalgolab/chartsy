package one.chartsy.ui.chart.data;

import one.chartsy.commons.Range;

public record VisualRange(Range range, boolean isLogarithmic) {

    public VisualRange(Range range) {
        this(range, false);
    }

    public double getMin() {
        return range().getMin();
    }

    public double getMax() {
        return range().getMax();
    }
}
