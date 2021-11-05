package one.chartsy.data;

import one.chartsy.data.packed.PackedDoubleDataset;
import one.chartsy.time.Timeline;

import java.util.function.DoubleBinaryOperator;

public class PackedDoubleSeries implements DoubleSeries {

    private final Timeline timeline;
    private final DoubleDataset values;

    public PackedDoubleSeries(Timeline timeline, DoubleDataset values) {
        this.timeline = timeline;
        this.values = values; //TODO: ...= PackedDoubleDataset.from(values)
    }

    public static PackedDoubleSeries empty(Timeline timeline) {
        return new PackedDoubleSeries(timeline, PackedDoubleDataset.of(new double[0]));
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
    public PackedDoubleSeries add(DoubleSeries y) {
        return mapThread(Double::sum, y);
    }

    @Override
    public PackedDoubleSeries mapThread(DoubleBinaryOperator f, DoubleSeries other) {
        requireSameTimeline(this, other);

        double[] r = new double[Math.min(length(), other.length())];
        for (int i = r.length-1; i >= 0; i--)
            r[i] = f.applyAsDouble(get(i), other.get(i));
        return DoubleSeries.of(r, getTimeline());
    }

    private static void requireSameTimeline(DoubleSeries a, DoubleSeries b) {
        if (a.getTimeline() != b.getTimeline())
            throw new IllegalArgumentException("Timeline mismatch between series");

    }

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
    public PackedDoubleSeries sma(int periods) {
        if (periods <= 0)
            throw new IllegalArgumentException("The `periods` argument " + periods + " must be positive integer");
        int newLength = length() - periods + 1;
        if (newLength <= 0)
            return DoubleSeries.empty(getTimeline());

        double[] result = new double[newLength];
        double value = 0.0;
        int i = length();
        for (int k = 0; k < periods; k++)
            value += get(--i);

        double coeff = 1.0/periods;
        result[i] = value *= coeff;
        while (--i >= 0)
            result[i] = value += ((get(i) - get(i + periods))*coeff);
        return DoubleSeries.of(result, getTimeline());
    }

    /**
     * Computes the <i>Wilders Moving Average</i> of the price series.
     * <p>
     * The Wilders MA was developed by Welles Wilder.<br>
     * The Wilders MA is a variation of an exponential moving average and is
     * used for computation other indicators developed by the same author, such
     * as {@link Quotes#rsi(int) RSI}, {@link Quotes#atr(int) ATR} and
     * {@link Quotes#adx(int) ADX}.
     *
     * @param periods
     *            the moving average smoothing parameter
     * @return the Wilders moving average series of length {@code n-periods+1},
     *         where {@code n} is the length of {@code this} series
     * @throws IllegalArgumentException
     *             if the {@code periods} parameter is not positive
     */
    public PackedDoubleSeries wilders(int periods) {
        if (periods <= 0)
            throw new IllegalArgumentException("The `periods` argument " + periods + " must be a positive integer");
        int newLength = length() - periods + 1;
        if (newLength <= 0)
            return empty(getTimeline());

        double[] result = new double[newLength];
        double value = 0.0;
        int i = length();
        for (int k = 0; k < periods; k++)
            value += get(--i);

        double alpha = 1.0 / periods;
        result[i] = value *= alpha;
        while (--i >= 0)
            result[i] = value += (get(i) - value)*alpha;
        return DoubleSeries.of(result, getTimeline());
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
