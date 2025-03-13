/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm.data;

import java.util.Comparator;

/**
 * RankingStrategy is an interface for computing a ranking score for instruments.
 * <p>
 * Implementations must define how to compute a numerical score for a given instrument.
 * This score is then used for ranking instruments. Depending on the desired ranking order,
 * a higher score may be considered better (e.g., for long positions) or a lower score may be
 * better (e.g., for short positions).
 * </p>
 * <p>
 * NaN scores are treated as the worst possible outcome – they are always sorted last,
 * regardless of whether the order is {@code HIGHER_BETTER} or {@code LOWER_BETTER}.
 * </p>
 *
 * @param <I> the type of InstrumentData to be ranked
 */
@FunctionalInterface
public interface RankingStrategy<I extends InstrumentData> {

    /**
     * Computes the ranking score for the given instrument.
     *
     * @param instr the instrument for which to compute the score
     * @return the computed score as a double; may be NaN if the score is undefined
     */
    double computeScore(I instr);

    /**
     * Returns a comparator for comparing instruments based on their computed ranking scores.
     * <p>
     * The comparator takes into account the desired ranking order by delegating the comparison
     * to the {@link RankingOrder#compare(double, double)} method.
     * </p>
     *
     * @param order the ranking order to use for comparison
     * @return a comparator for comparing two instruments based on their ranking scores
     */
    default Comparator<I> comparator(RankingOrder order) {
        return (i1, i2) -> {
            double s1 = computeScore(i1);
            double s2 = computeScore(i2);
            return order.compare(s1, s2);
        };
    }

    /**
     * RankingOrder defines the ordering criteria for ranking instruments.
     * <ul>
     *   <li>{@code HIGHER_BETTER}: A higher score is considered better and should rank higher (i.e., come first).</li>
     *   <li>{@code LOWER_BETTER}: A lower score is considered better and should rank higher (i.e., come first).</li>
     * </ul>
     * <p>
     * This enum provides a {@code compare} method which encapsulates the comparison logic,
     * including custom handling of NaN values. In this design, any score that is NaN is
     * always considered worse than any valid score.
     * </p>
     */
    enum RankingOrder {
        HIGHER_BETTER {
            @Override
            public int compare(double s1, double s2) {
                return Double.isNaN(s1) ? (Double.isNaN(s2) ? 0 : 1)
                        : Double.isNaN(s2) ? -1
                        : Double.compare(s2, s1);
            }
        },
        LOWER_BETTER {
            @Override
            public int compare(double s1, double s2) {
                return Double.isNaN(s1) ? (Double.isNaN(s2) ? 0 : 1)
                        : Double.isNaN(s2) ? -1
                        : Double.compare(s1, s2);
            }
        };

        public abstract int compare(double s1, double s2);
    }
}
