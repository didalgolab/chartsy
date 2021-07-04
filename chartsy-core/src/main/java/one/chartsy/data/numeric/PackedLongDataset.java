package one.chartsy.data.numeric;

import one.chartsy.data.LongDataset;

import java.util.Arrays;
import java.util.stream.LongStream;

public class PackedLongDataset implements LongDataset {
    private final long[] values;

    protected PackedLongDataset(long[] values) {
        this.values = values;
    }

    public static PackedLongDataset of(long[] values) {
        return new PackedLongDataset(values.clone());
    }

    public static PackedLongDataset from(LongDataset dataset) {
        if (dataset instanceof PackedLongDataset)
            return (PackedLongDataset) dataset;

        long[] values = new long[dataset.length()];
        Arrays.setAll(values, dataset::get);
        return new PackedLongDataset(values);
    }

    @Override
    public long get(int index) {
        return values[index];
    }

    @Override
    public int length() {
        return values.length;
    }

    @Override
    public LongStream stream() {
        return Arrays.stream(values);
    }
}
