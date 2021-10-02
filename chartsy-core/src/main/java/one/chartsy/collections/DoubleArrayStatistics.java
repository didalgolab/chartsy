package one.chartsy.collections;

import java.util.Arrays;

public class DoubleArrayStatistics {
    private final int count;
    private final double sum;
    private final double min;
    private final double max;
    private final double median;
    private final double percentile75;
    private final double percentile90;
    private final double percentile95;
    private final double percentile99;

    protected DoubleArrayStatistics(DoubleArray values) {
        Arrays.sort(values.data);
        this.count = values.size();
        this.sum = values.sum();
        this.min = values.get(0);
        this.max = values.get(count - 1);
        this.median = (count == 0)? Double.NaN : values.get(count / 2);
        this.percentile75 = (count == 0)? Double.NaN : values.get(count * 3 / 4);
        this.percentile90 = (count == 0)? Double.NaN : values.get(count * 9 / 10);
        this.percentile95 = (count == 0)? Double.NaN : values.get(count * 19 / 20);
        this.percentile99 = (count == 0)? Double.NaN : values.get(count * 99 / 100);
    }

    public int getCount() {
        return count;
    }

    public double getSum() {
        return sum;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getAverage() {
        return getSum() / getCount();
    }

    public double getMedian() {
        return median;
    }

    public double get75thPercentile() {
        return percentile75;
    }

    public double get90thPercentile() {
        return percentile90;
    }

    public double get95thPercentile() {
        return percentile95;
    }

    public double get99thPercentile() {
        return percentile99;
    }

    @Override
    public String toString() {
        return "{\"cnt\":" + getCount()
                + ", \"min\":" + getMin()
                + ", \"avg\":" + getAverage()
                + ", \"median\":" + getMedian()
                + ", \"75%\":" + get75thPercentile()
                + ", \"90%\":" + get90thPercentile()
                + ", \"95%\":" + get95thPercentile()
                + ", \"99%\":" + get99thPercentile()
                + ", \"max\":" + getMax() + "}";
    }
}
