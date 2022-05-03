package one.chartsy.data;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.packed.PackedCandleSeries;
import one.chartsy.data.packed.PackedDataset;
import one.chartsy.random.RandomWalk;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

public interface CandleSeries extends Series<Candle> {

    /**
     * Gives a transformer capable of transforming a mono list of candles into the {@code CandleSeries}
     * of the given resource.
     *
     * @param resource the target series symbol resource
     * @return the transformer
     */
    static Function<? super Mono<List<Candle>>, CandleSeries> of(SymbolResource<Candle> resource) {
        return mono -> of(resource, mono.block());
    }

    static CandleSeries of(SymbolResource<Candle> resource, Collection<? extends Candle> values) {
        return new PackedCandleSeries(resource, PackedDataset.of(values));
    }

    static <T extends Candle> CandleSeries from(Series<T> series) {
        if (series instanceof CandleSeries)
            return (CandleSeries) series;

        return new PackedCandleSeries((SymbolResource<Candle>) series.getResource(), (Dataset<Candle>) series.getData());
    }

    default TimeFrame getTimeFrame() {
        return getResource().timeFrame();
    }

    default DoubleSeries highs() {
        return mapToDouble(Candle::high);
    }

    default DoubleSeries closes() {
        return mapToDouble(Candle::close);
    }

    default DoubleSeries volumes() {
        return mapToDouble(Candle::volume);
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
     * @see #atrp(int)
     * @see #trueRange()
     */
    DoubleSeries atr(int periods);

    /**
     * Computes the Average True Range Percentage indicator.
     * <p>
     * The Average True Range Percentage is commonly abbreviated as ATRP.<br>
     * The ATRP is a volatility indicator.<br>
     * The ATRP is computed using high, low and close prices.<br>
     * The ATRP is very similar to the {@link #atr(int) Average True Range}
     * except that the ATRP is normalized to express percentage of recent price
     * variation in the range between {@code 0} and {@code 100}, while the
     * Average True Range gives volatility in absolute price variation.
     *
     * @param periods
     *            the indicator averaging period
     * @return the single time series of length {@code this.length()-periods+1}
     *         with the Wilder's moving average of the true range percent
     * @throws IllegalArgumentException
     *             if {@code periods} parameter is not positive
     */
    DoubleSeries atrp(int periods);

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
    DoubleSeries trueRange();

    default DoubleSeries highestSince() {
        return highs().highestSince();
    }

    /**
     * Produces a random resample of a CandleSeries using sampling with replacement.
     *
     * @return a resampled series having the same size as the original one
     */
    default CandleSeries resample(AdjustmentMethod method) {
        return RandomWalk.bootstrap(this, ThreadLocalRandom.current(), method);
    }
}
