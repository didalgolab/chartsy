/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.random;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.base.dataset.ImmutableDataset;
import one.chartsy.data.AdjustmentFunction;
import one.chartsy.data.AdjustmentMethod;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.Series;
import one.chartsy.data.packed.PackedCandleSeries;
import one.chartsy.time.Chronological;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

public class RandomWalk {

    public static Candle candle(long time) {
        return candle(time, 0.0, RandomCandleSpecification.BASIC, ThreadLocalRandom.current());
    }

    public static Candle candle(long time, double referencePrice, RandomCandleSpecification spec, RandomGenerator rnd) {
        double open = referencePrice + rnd.nextGaussian(spec.drift(), spec.stddev()) * spec.gappiness();
        double curr = open;
        double min = curr;
        double max = curr;
        for (int i = 0; i < 4; i++) {
            curr += rnd.nextGaussian(spec.drift(), spec.stddev());
            min = Math.min(min, curr);
            max = Math.max(max, curr);
        }
        return Candle.of(time, open, max, min, curr);
    }

    public static CandleSeries candleSeries(int count, SymbolResource<Candle> symbol) {
        List<Candle> list = RandomWalk.candles()
                .limit(count)
                .toList();
        list = new ArrayList<>(list);
        Collections.reverse(list);
        return CandleSeries.of(symbol, list);
    }

    public static Stream<Candle> candles() {
        return candles(LocalDate.ofEpochDay(0).atStartOfDay(), 0.0, Duration.ofDays(1), RandomCandleSpecification.BASIC, ThreadLocalRandom.current());
    }

    public static Stream<Candle> candles(Duration granularity, LocalDateTime startTime) {
        return candles(startTime, 0.0, granularity, RandomCandleSpecification.BASIC, ThreadLocalRandom.current());
    }

    public static Stream<Candle> candles(LocalDateTime startTime, double referencePrice, Duration granularity, RandomCandleSpecification spec, RandomGenerator rnd) {
        var timeStepNanos = Chronological.toNanos(granularity);
        var seedEndTime = Chronological.toEpochNanos(startTime) + timeStepNanos;
        var seed = candle(seedEndTime, referencePrice, spec.withGappiness(0.0), rnd);
        return Stream.iterate(seed, c -> candle(c.getTime() + timeStepNanos, c.close(), spec, rnd));
    }

    /**
     * Performs a time series bootstrapping of the specified series using default
     * source of randomness.
     * <p>
     * The method utilizes the <i>drawing with replacement</i> idea.<br>
     * The generated series have the same {@link Series#length() length} and
     * {@link Series#getTimeline() Timeline} as the origin.<br>
     * The generated series have an artificial {@link SymbolIdentity} created and
     * with the original name prepended with a <i>tilde</i> character. For example
     * a tradeable symbol <i>"EURUSD"</i> will have a bootstrap symbol <i>"~EURUSD"</i>.
     *
     * <p>
     * <b>Code examples</b><br>
     * Use the following code to obtain a single bootstrap series for the given
     * {@code series}:
     *
     * <pre>{@code Series<Candle> newSample = RandomWalk.bootstrap(series);}</pre>
     *
     * <p>
     * Use the following code to obtain an infinite stream of bootstrap series
     * for the given {@code series}:
     *
     * <pre>{@code Stream<Series<Candle>> newSamples = Stream.generate(RandomWalk::bootstrap);}</pre>
     *
     * <p>
     * When having the set of quotes with different symbols, use the following
     * code to replace each quotes with its bootstrap at one single step:
     *
     * <pre>
     * {@code
     * Collection<Quotes> marketData = ...
     * marketData.replaceAll(Quotes::bootstrap);
     * }
     * </pre>
     *
     * <p>
     * When deterministic randomization strategy is required, use an externally
     * seeded random generator (not recommended in normal case, since
     * statistical value of bootstrap methods usually depend on <i>large</i>
     * number of <i>nondeterministic</i> samples):
     *
     * <pre>
     * {@code
     * long seed = ...
     * Random rand = new Random(seed);
     * ... = quotes.randomWalk(rand);
     * }
     * </pre>
     *
     * @implSpec The default implementation is equivalent to, for this
     *           {@code quotes}:
     * <pre>
     * {@code quotes.randomWalk(ThreadLocalRandom.current())}
     * </pre>
     *
     * @return the sequence of quotes randomly resampled with replacement from
     *         the original one
     */
    public static CandleSeries bootstrap(Series<Candle> origin) {
        return bootstrap(origin, ThreadLocalRandom.current());
    }

    public static CandleSeries bootstrap(Series<Candle> origin, RandomGenerator rnd) {
        return bootstrap(origin, rnd, AdjustmentMethod.RELATIVE);
    }

    public static CandleSeries bootstrap(Series<Candle> origin, RandomGenerator rnd, AdjustmentFunction method) {
        int[] mapping = (origin.length() == 0)? new int[0] : rnd.ints(origin.length(), 0, origin.length()).toArray();
        return bootstrap(origin, index -> mapping[index], method);
    }

    public static CandleSeries bootstrap(Series<Candle> series, IntUnaryOperator mapping, AdjustmentFunction method) {
        int barCount = series.length();

        // Fill the resulting Quote array
        Candle[] result = new Candle[barCount];
        if (barCount > 0) {
            double ref = series.get(barCount - 1).open();
            for (int barNo = barCount - 1; barNo >= 0; barNo--) {
                int index = mapping.applyAsInt(barNo);
                Candle choice = series.get(index);
                Candle source = series.get(barNo);
                double open = (index + 1 == series.length())? ref : method.calculate(series.get(index + 1).close(), choice.open(), ref);
                double close = method.calculate(choice.open(), choice.close(), open);
                result[barNo] = Candle.of(
                        source.getTime(),
                        open,
                        method.calculate(choice.open(), choice.high(), open),
                        method.calculate(choice.open(), choice.low(), open),
                        close,
                        choice.volume());
                ref = close;
            }
        }
        var resource = series.getResource();
        var symbol = resource.symbol();
        return new PackedCandleSeries(resource.withSymbol(SymbolIdentity.of("~" + symbol.name() + "~", symbol.type())), ImmutableDataset.ofReversedSameIndexingOrder(result));
    }

    private RandomWalk() { }
}
