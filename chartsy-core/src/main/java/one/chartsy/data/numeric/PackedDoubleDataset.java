package one.chartsy.data.numeric;

import one.chartsy.data.DoubleDataset;

import java.util.Arrays;
import java.util.stream.DoubleStream;

public class PackedDoubleDataset implements DoubleDataset {
    private final double[] values;

    protected PackedDoubleDataset(double[] values) {
        this.values = values;
    }

    public static PackedDoubleDataset of(double[] values) {
        return new PackedDoubleDataset(values.clone());
    }

    public static PackedDoubleDataset from(DoubleDataset dataset) {
        if (dataset instanceof PackedDoubleDataset)
            return (PackedDoubleDataset) dataset;

        double[] values = new double[dataset.length()];
        Arrays.setAll(values, dataset::get);
        return new PackedDoubleDataset(values);
    }

    @Override
    public double get(int index) {
        return values[index];
    }

    @Override
    public int length() {
        return values.length;
    }

    @Override
    public DoubleStream stream() {
        return Arrays.stream(values);
    }
}
