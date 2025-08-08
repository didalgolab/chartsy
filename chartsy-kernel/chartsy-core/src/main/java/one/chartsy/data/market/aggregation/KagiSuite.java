package one.chartsy.data.market.aggregation;

import one.chartsy.Candle;
import one.chartsy.data.ReversalAmount;

import java.util.*;
import java.util.stream.Stream;

/**
 * A coordinated set of {@link KagiIndicator}s, each with a different reversal amount, that can collectively be updated
 * with new candles. This class simplifies managing multiple Kagi indicators simultaneously, such as when
 * scanning for patterns across a range of reversal amounts.
 *
 * <p>Typical usage:</p>
 * <pre>
 * KagiSuite suite = KagiSuite.ofPercentageRange(5.0, 0.05, 200);
 * for (Candle candle : candles) {
 *     suite.onCandle(candle);
 *     suite.stream().forEach(indicator -> ...);
 * }
 * </pre>
 */
public final class KagiSuite implements Iterable<KagiIndicator> {

    private final List<KagiIndicator> indicators;

    /**
     * Private constructor to enforce factory method usage.
     *
     * @param indicators list of initialized {@link KagiIndicator}s
     */
    private KagiSuite(List<KagiIndicator> indicators) {
        this.indicators = indicators;
    }

    /**
     * Creates a {@code KagiSuite} consisting of a sequence of {@link KagiIndicator}s,
     * each having a reversal amount expressed as a percentage.
     *
     * <p>The suite is generated with reversal percentages starting at {@code start}, incrementing by {@code step},
     * for a total of {@code count} indicators.</p>
     *
     * @param start the initial reversal percentage
     * @param step  the incremental step between consecutive indicators' reversal percentages
     * @param count the number of {@code KagiIndicator}s to generate
     * @return a new {@code KagiSuite} instance containing the specified indicators
     */
    public static KagiSuite ofPercentageRange(double start, double step, int count) {
        List<KagiIndicator> indicators = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double percentage = start + step * i;
            indicators.add(new KagiIndicator(ReversalAmount.ofPercentage(percentage)));
        }
        return new KagiSuite(indicators);
    }

    /**
     * Updates each {@link KagiIndicator} in this suite with the provided {@link Candle}.
     *
     * @param candle the latest candle to be processed by each indicator
     */
    public void onCandle(Candle candle) {
        indicators.forEach(indicator -> indicator.onCandle(candle));
    }

    /**
     * Returns an iterator over the {@link KagiIndicator}s in this suite.
     *
     * @return an {@link Iterator} of indicators
     */
    @Override
    public Iterator<KagiIndicator> iterator() {
        return indicators.iterator();
    }

    /**
     * Provides a sequential {@link Stream} of the {@link KagiIndicator}s contained in this suite.
     * Useful for functional-style processing, filtering, and mapping.
     *
     * @return a sequential {@link Stream} of indicators
     */
    public Stream<KagiIndicator> stream() {
        return indicators.stream();
    }

    /**
     * Returns the number of {@link KagiIndicator}s within this suite.
     *
     * @return indicator count
     */
    public int size() {
        return indicators.size();
    }

    /**
     * Retrieves an unmodifiable view of the indicators contained in this suite.
     *
     * @return an unmodifiable {@link List} of indicators
     */
    public List<KagiIndicator> getIndicators() {
        return Collections.unmodifiableList(indicators);
    }
}
