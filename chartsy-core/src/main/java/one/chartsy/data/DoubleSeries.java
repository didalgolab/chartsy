package one.chartsy.data;

import one.chartsy.data.packed.PackedDoubleDataset;
import one.chartsy.data.packed.PackedDoubleSeries;
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

    default DoubleSeries sub(DoubleSeries y) {
        return mapThread(DoubleSeriesSupport::subtract, y);
    }

    /**
     * Multiplies the series by the specified constant value.
     *
     * @param y
     *            the value to be multiplied by
     * @return the new time series representing product of {@code this} series
     *         and {@code y}
     */
    DoubleSeries mul(double y);

    /**
     * Performs an element-wise division of {@code this} and {@code y} series.
     *
     * @param y
     *            the divisors
     * @return the sequence of quotients, of length
     *         {@code min(this.length(), y.length())}
     * @throws IllegalArgumentException
     *             if {@code this} and {@code y} series differ in the time frame
     */
    DoubleSeries div(DoubleSeries y);

    /**
     * Gives the simple moving average of the series, computed by taking the
     * arithmetic mean over the specified {@code periods}.
     * <p>
     * The simple moving average is commonly abbreviated as SMA.<br>
     * The SMA is a statistical indicator.<br>
     * For example the calculation of {@code sma(2)} over the series {3, 9, 9,
     * -8, 6} gives {6, 9, 0.5, -1}. As a special corner case, the
     * {@code sma(1)} gives the result matching the original series.
     *
     * @param periods
     *            the moving average period
     * @return the series of arithmetic means, of length
     *         {@code this.length()-periods+1}
     * @throws IllegalArgumentException
     *             if the {@code periods} argument is not positive
     */
    DoubleSeries sma(int periods);

    /**
     * Computes the <i>Wilders Moving Average</i> of the price series.
     * <p>
     * The Wilders MA was developed by Welles Wilder.<br>
     * The Wilders MA is a variation of an exponential moving average and is
     * used for computation other indicators developed by the same author, such
     * as {@link CandleSeries#rsi(int) RSI}, {@link CandleSeries#atr(int) ATR} and
     * {@link CandleSeries#adx(int) ADX}.
     *
     * @param periods
     *            the moving average smoothing parameter
     * @return the Wilders moving average series of length {@code n-periods+1},
     *         where {@code n} is the length of {@code this} series
     * @throws IllegalArgumentException
     *             if the {@code periods} parameter is not positive
     */
    DoubleSeries wilders(int periods);

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
