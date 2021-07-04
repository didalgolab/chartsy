package one.chartsy.data;

import one.chartsy.data.numeric.PackedDoubleDataset;

import java.util.function.DoubleUnaryOperator;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

/**
 * A dataset specialized for double values.
 *
 * @author Mariusz Bernacki
 */
public interface DoubleDataset extends SequenceAlike<Double, DoubleStream, DoubleDataset> {

    double get(int index);

    @Override
    default DoubleStream stream() {
        return IntStream.range(0, length()).mapToDouble(this::get);
    }

    @Override
    default DoubleDataset take(int start, int count) {
        if (start < 0)
            throw new IllegalArgumentException("The `start` argument cannot be negative");
        if (count <= 0)
            throw new IllegalArgumentException("The `count` argument must be positive");
        if (length() < count - start)
            throw new IllegalArgumentException("The take end index cannot exceed dataset length " + length());

        //return new Partition()
        return new DoubleDataset() {
            @Override
            public int length() {
                return count;
            }

            @Override
            public double get(int index) {
                return DoubleDataset.this.get(start + Datasets.requireValidIndex(index, this));
            }
        };
    }

    default DoubleDataset map(DoubleUnaryOperator valueMapping) {
        return new MappedDoubleDataset(this, DoubleDataset::length,
                (ds, idx) -> valueMapping.applyAsDouble(ds.get(idx)));
    }

    default DoubleDataset add(double x) {
        return map(v -> v + x);
    }

    default DoubleDataset sub(double x) {
        return add(-x);
    }

    default DoubleDataset mul(double x) {
        return map(v -> v * x);
    }

    default DoubleDataset div(double x) {
        return map(v -> v / x);
    }

    default DoubleDataset differences() {
        return new MappedDoubleDataset(this, ds -> ds.length()-1, (ds, idx) -> ds.get(idx) - ds.get(idx + 1));
    }

    default DoubleDataset ratios() {
        return new MappedDoubleDataset(this, ds -> ds.length()-1, (ds, idx) -> ds.get(idx) / ds.get(idx + 1));
    }

    default DoubleDataset toDirect() {
        return PackedDoubleDataset.from(this);
    }
}
