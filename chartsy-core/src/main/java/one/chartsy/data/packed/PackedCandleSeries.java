package one.chartsy.data.packed;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.data.*;

public class PackedCandleSeries extends PackedSeries<Candle> implements CandleSeries {

    public PackedCandleSeries(SymbolResource<Candle> resource, Dataset<Candle> dataset) {
        super(resource, dataset);
    }

    public static PackedCandleSeries from(Series<? extends Candle> series) {
        if (series instanceof PackedCandleSeries)
            return (PackedCandleSeries) series;

        @SuppressWarnings("unchecked")
        var cs = (Series<Candle>) series;
        return new PackedCandleSeries(cs.getResource(), PackedDataset.from(cs.getData()));
    }

    @Override
    public String toString() {
        return getResource() + ": " /*+ getData()*/;
    }

    /**
     * Gives the normal trading range, high to low, including any gap between
     * today's high or low and yesterday's close of the underlying bars.
     * <p>
     * The True Range is a volatility indicator developed by Welles Wilder.<br>
     * The indicator is computed using high, low and close prices.<br>
     * The trading range is measured as an absolute price difference (not in a
     * percentage change).
     *
     * @return the single time series of length {@code this.length()-1} with
     *         maximum difference between the current high, low and previous
     *         close prices
     * @see #atr(int)
     */
    public PackedDoubleSeries trueRange() {
        int newLength = length() - 1;
        if (newLength <= 0)
            return DoubleSeries.empty(getTimeline());

        Candle c2 = get(newLength);
        double[] result = new double[newLength];
        for (int i = newLength - 1; i >= 0; i--) {
            Candle c1 = get(i);
            result[i] = Math.max(c1.high(), c2.close()) - Math.min(c1.low(), c2.close());
            c2 = c1;
        }
        return DoubleSeries.of(result, getTimeline());
    }

    /**
     * Computes the Average True Range indicator.
     * <p>
     * The Average True Range is commonly abbreviated as ATR.<br>
     * The ATR is a volatility indicator developed by Welles Wilder.<br>
     * The ATR is computed using high, low and close prices.<br>
     * The method is effectively equivalent to, for {@code this} series:
     *
     * <pre>
     * {@code this.trueRange().wilders(periods)}
     * </pre>
     *
     * @param periods
     *            the indicator averaging period
     * @return the single time series of length {@code this.length()-periods+1}
     *         with the Wilder's moving average of the true range
     * @throws IllegalArgumentException
     *             if {@code periods} parameter is not positive
     * //@see #atrp(int)
     * @see #trueRange()
     */
    public PackedDoubleSeries atr(int periods) {
        return trueRange().wilders(periods);
    }
}
