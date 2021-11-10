package one.chartsy.data;

import one.chartsy.data.packed.PackedDoubleDataset;
import one.chartsy.time.Timeline;

import java.util.function.DoubleBinaryOperator;

/**
 * Primitive equivalent of {@code Series} suitable for holding double values.
 */
public interface DoubleSeries extends TimeSeriesAlike {

    double get(int index);

    DoubleDataset values();

    static PackedDoubleSeries of(double[] values, Timeline timeline) {
        return new PackedDoubleSeries(timeline, PackedDoubleDataset.of(values));
    }

    static PackedDoubleSeries empty(Timeline timeline) {
        return of(new double[0], timeline);
    }

    default DoubleSeries add(DoubleSeries y) {
        return mapThread(Double::sum, y);
    }

    default DoubleSeries max(DoubleSeries y) {
        return mapThread(Double::max, y);
    }

    default DoubleSeries min(DoubleSeries y) {
        return mapThread(Double::min, y);
    }

    /**
     * Applies the binary function to each element of both this and the other
     * series to produce a new time series.
     * <p>
     * The elements from {@code this} series are used as the <i>left</i>
     * arguments of the function {@code f}.<br>
     * The elements from the {@code other} series are used as the <i>right</i>
     * arguments of the function {@code f}.<br>
     * The given operator {@code f} is applied to each series value one by one,
     * starting from the farthest possible element to the closest one at
     * {@code index = 0}. The resulting series have length equal to the length
     * of the shortest of {@code this} and the {@code other} series. Both series
     * must have the same {@link #getTimeline() timeline}, otherwise an
     * {@code IllegalArgumentException} is thrown.
     *
     * @param f
     *            the real-valued mapping function to use
     * @param other
     *            the other series whose elements are to be applied to the
     *            function {@code f}
     * @return the new time series
     */
    DoubleSeries mapThread(DoubleBinaryOperator f, DoubleSeries other);

}
