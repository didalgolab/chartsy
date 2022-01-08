package one.chartsy.data.packed;

import one.chartsy.data.DoubleDataset;
import one.chartsy.data.DoubleSeries;
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

    @Override
    public PackedDoubleSeries ref(int periods) {
        return new PackedDoubleSeries(getTimeline(), values().ref(periods));
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
    public PackedDoubleSeries hhv(int periods) {
        // check if periods argument is valid
        if (periods <= 0)
            throw new IllegalArgumentException("The periods argument must be positive, but was " + periods);
        if (periods == 1)
            return this;

        int newLength = length() - periods + 1;
        if (newLength <= 0)
            return empty(getTimeline());

        // create deque having length = 2^k
        int mask = Integer.highestOneBit(periods)*2 - 1;
        int[] window = new int[mask + 1];
        double[] z = new double[newLength];

        // pre-fill the window array
        int first = 0, last = 0;
        int i = length(), j = z.length;
        while (--i >= j - 1) {
            while (last > 0 && get(window[last - 1]) <= get(i))
                last--;
            window[last++] = i;
        }
        z[--j] = get(window[first]);

        // main algorithm loop
        while (i >= 0) {
            while (window[first & mask] >= i + periods)
                first++;
            while (first != last && get(window[(last - 1) & mask]) <= get(i))
                last--;
            window[last++ & mask] = i--;
            z[--j] = get(window[first & mask]);
        }
        return DoubleSeries.of(z, getTimeline());
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
    public PackedDoubleSeries llv(int periods) {
        // check if periods argument is valid
        if (periods <= 0)
            throw new IllegalArgumentException("\"periods\" must be positive");
        if (periods == 1)
            return this;

        int length = length();
        if (length - periods + 1 <= 0)
            return empty(getTimeline());

        // create deque having length = 2^k
        int mask = Integer.highestOneBit(periods)*2 - 1;
        int[] window = new int[mask + 1];
        double[] z = new double[length - periods + 1];

        // pre-fill the window array
        int first = 0, last = 0;
        int i = length, j = z.length;
        while (--i >= j - 1) {
            while (last > 0 && get(window[last - 1]) >= get(i))
                last--;
            window[last++] = i;
        }
        z[--j] = get(window[first]);

        // main algorithm loop
        while (i >= 0) {
            while (window[first & mask] >= i + periods)
                first++;
            while (first != last && get(window[(last - 1) & mask]) >= get(i))
                last--;
            window[last++ & mask] = i--;
            z[--j] = get(window[first & mask]);
        }
        return DoubleSeries.of(z, getTimeline());
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
