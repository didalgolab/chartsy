/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial;

/**
 * A state object representing a generic indicator used for analyzing financial data.
 *
 * <p>Implementations of this interface are almost always <b>stateful</b> and
 * <b>not thread-safe</b>.</p>
 *
 * @author Mariusz Bernacki
 * @see BandValueIndicator
 */
public interface ValueIndicator {

    /**
     * Checks if the indicator has enough data to produce a valid results.
     *
     * @return {@code true} if the indicator value is ready, {@code false} otherwise
     */
    boolean isReady();

    /**
     * Specialized indicator producing {@code double} values from data.
     */
    interface OfDouble extends ValueIndicator {

        /**
         * Returns the most recent calculated value of the indicator.
         *
         * @return the last calculated value
         */
        double getLast();
    }

    /**
     * Specialized indicator producing generic {@code <T>} values values from data.
     *
     * @param <T> the type of output values
     */
    interface Of<T> extends ValueIndicator {

        /**
         * Returns the most recent calculated value of the indicator.
         *
         * @return the last calculated value
         */
        T getLast();
    }
}