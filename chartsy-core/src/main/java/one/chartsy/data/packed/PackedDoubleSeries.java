package one.chartsy.data.packed;

import one.chartsy.data.DoubleDataset;
import one.chartsy.data.DoubleSeries;
import one.chartsy.data.Series;
import one.chartsy.time.Timeline;

import java.util.function.DoubleBinaryOperator;

public class PackedDoubleSeries extends AbstractDoubleSeries<PackedDoubleSeries> implements DoubleSeries {

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
    public PackedDoubleSeries mapThread(DoubleBinaryOperator f, double rightValue) {
        double[] z = new double[length()];
        for (int i = z.length-1; i >= 0; i--)
            z[i] = f.applyAsDouble(get(i), rightValue);

        return DoubleSeries.of(z, getTimeline());
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
    @Override
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

    @Override
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
    public PackedDoubleSeries highestSince() {
        double[] result = new double[length()];

        for (int barNo = 0; barNo < result.length; barNo++) {
            double high = get(barNo);

            int index;
            for (index = barNo+1; index < result.length; index++)
                if (get(index) > high)
                    break;
            result[barNo] = index - barNo;
        }
        return DoubleSeries.of(result, getTimeline());
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
