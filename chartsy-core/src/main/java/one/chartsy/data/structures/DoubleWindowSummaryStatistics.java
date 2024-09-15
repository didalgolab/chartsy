/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.structures;

import one.chartsy.collections.IntArrayDeque;

import java.util.Arrays;
import java.util.DoubleSummaryStatistics;
import java.util.Locale;
import java.util.function.DoubleConsumer;

/**
 * A state object for collecting sliding window statistics such as count, min, max, sum, and
 * average over a fixed-size window of double values.
 *
 * <p>Unlike {@link DoubleSummaryStatistics}, this class maintains statistics
 * over a fixed-size sliding window. When the number of recorded values exceeds the
 * window size, the oldest values are discarded as new ones are added.
 *
 * <p>This implementation uses Kahan summation for improved accuracy of the sum calculation.
 * It also employs efficient data structures (circular buffer and deques) to maintain
 * the sliding window and track minimum and maximum values.
 *
 * @implNote This implementation is not thread-safe.
 *
 * <p>This implementation does not check for overflow of the count total.
 *
 * @apiNote The implementation of this class was highly inspired by
 * {@link DoubleSummaryStatistics} from the Java Development Kit (JDK).
 * The sliding window functionality and efficient min/max tracking are custom additions.
 *
 * @author Mariusz Bernacki
 * @see DoubleSummaryStatistics
 */
public class DoubleWindowSummaryStatistics implements DoubleConsumer {
    private final int windowSize;
    private final double[] values;
    private final IntArrayDeque minDeque;
    private final IntArrayDeque maxDeque;
    private int lastIndex;
    private double sum;
    private double sumCompensation; // Low order bits of sum
    private double simpleSum; // Used to compute right sum for non-finite inputs
    private long countTotal;

    /**
     * Constructs an empty instance with zero count, zero sum,
     * {@code Double.POSITIVE_INFINITY} min, {@code Double.NEGATIVE_INFINITY} max
     * and zero average.
     *
     * @param windowSize the size of the sliding window
     * @throws IllegalArgumentException if windowSize is less than two
     */
    public DoubleWindowSummaryStatistics(int windowSize) {
        if (windowSize < 2)
            throw new IllegalArgumentException("Window size must >= 2, but was: " + windowSize);

        this.windowSize = windowSize;
        this.lastIndex = windowSize - 1;
        this.values = new double[windowSize];
        this.minDeque = new IntArrayDeque(windowSize);
        this.maxDeque = new IntArrayDeque(windowSize);
    }

    /**
     * Records another value into the sliding summary information.
     * This method is equivalent to {@link #add(double)}.
     *
     * @param value the input value
     */
    @Override
    public void accept(double value) {
        add(value);
    }

    /**
     * Records another value into the sliding summary information.
     *
     * @param value the input value
     * @return the value that was removed from the window, or {@code 0} if the window wasn't full
     */
    public double add(double value) {
        ++countTotal;
        if (++lastIndex >= windowSize) {
            lastIndex = 0;
        }

        double oldValue = values[lastIndex];
        simpleSum += value - oldValue;
        sumWithCompensation(-oldValue);
        sumWithCompensation(value);
        updateMinMax(value);
        values[lastIndex] = value;

        return oldValue;
    }

    private void updateMinMax(double value) {
        // Update deques for new window
        while (!minDeque.isEmpty() && value <= values[minDeque.getLast()])
            minDeque.pollLast();
        while (!maxDeque.isEmpty() && value >= values[maxDeque.getLast()])
            maxDeque.pollLast();

        // Pop older element outside window from deques
        if (!minDeque.isEmpty() && minDeque.getFirst() == lastIndex)
            minDeque.pollFirst();
        if (!maxDeque.isEmpty() && maxDeque.getFirst() == lastIndex)
            maxDeque.pollFirst();

        // Insert current element in deques
        minDeque.offerLast(lastIndex);
        maxDeque.offerLast(lastIndex);
    }

    /**
     * Incorporate a new double value using Kahan summation / compensated summation.
     */
    private void sumWithCompensation(double value) {
        double tmp = value - sumCompensation;
        double velvel = sum + tmp; // Little wolf of rounding error
        sumCompensation = (velvel - sum) - tmp;
        sum = velvel;
    }

    /**
     * Return the count of values within the current window.
     *
     * @return the count of values in the window
     */
    public final long getCount() {
        return Math.min(countTotal, windowSize);
    }

    /**
     * Returns the total count of values that have been added since this
     * object was created or reset.
     *
     * @return the total count of values added
     */
    public final long getCountTotal() {
        return countTotal;
    }

    /**
     * Returns the sum of values in the current window.
     *
     * @return the sum of values in the current window,
     *         or zero if the window is empty
     */
    public final double getSum() {
        // Better error bounds to add both terms as the final sum
        double tmp =  sum - sumCompensation;
        if (Double.isNaN(tmp) && Double.isInfinite(simpleSum))
            // If the compensated sum is spuriously NaN from
            // accumulating one or more same-signed infinite values,
            // return the correctly-signed infinity stored in
            // simpleSum.
            return simpleSum;
        else
            return tmp;
    }

    /**
     * Returns the minimum value in the current window.
     *
     * @return the minimum value in the current window,
     *         or {@code Double.POSITIVE_INFINITY} if the window is empty
     */
    public final double getMin() {
        return getCountTotal() == 0 ? Double.POSITIVE_INFINITY : values[minDeque.getFirst()];
    }

    /**
     * Returns the maximum value in the current window.
     *
     * @return the maximum value in the current window,
     *         or {@code Double.NEGATIVE_INFINITY} if the window is empty
     */
    public final double getMax() {
        return getCountTotal() == 0 ? Double.NEGATIVE_INFINITY : values[maxDeque.getFirst()];
    }

    /**
     * Returns the arithmetic mean of values in the current window.
     *
     * @return the arithmetic mean of values in the current window,
     *         or zero if the window is empty
     */
    public final double getAverage() {
        var count = getCount();
        return count > 0 ? getSum() / count : 0.0d;
    }

    /**
     * Checks if the sliding window is empty.
     *
     * <p>The window is considered empty if no values have been added to it.
     *
     * @return {@code true} if the sliding window contains no elements, {@code false} otherwise
     */
    public final boolean isEmpty() {
        return getCountTotal() == 0;
    }

    /**
     * Checks if the sliding window is full.
     *
     * <p>The window is considered full when the number of values added
     * is equal to or greater than the window size. Once full, the window
     * remains full indefinitely as new values replace old ones.
     *
     * @return {@code true} if the sliding window is at full capacity, {@code false} otherwise
     */
    public final boolean isFull() {
        return getCountTotal() >= windowSize;
    }

    /**
     * Returns the first (oldest) element in the sliding window.
     *
     * <p>If the window is not yet full, this method returns the first element that was added to the window.
     * If the window is full, it returns the oldest element currently in the window. If the window is empty, it
     * returns 0.
     *
     * @return the first (oldest) element in the sliding window, or 0 if the window is empty
     */
    public double getFirst() {
        if (!isFull()) {
            return values[0];
        }
        int firstValueIndex = lastIndex + 1;
        return values[(firstValueIndex >= windowSize) ? firstValueIndex - windowSize : firstValueIndex];
    }

    /**
     * Returns the last (newest) element in the sliding window.
     *
     * <p>This method always returns the most recently added element to the window,
     * regardless of whether the window is full or not. If the window is empty,
     * it returns 0.
     *
     * @return the last (newest) element in the sliding window, or 0 if the window is empty
     */
    public double getLast() {
        return values[lastIndex];
    }

    /**
     * Retrieves a past value from the sliding window.
     *
     * @param index the index of the past value to retrieve, where 0 is the most recent value,
     *              1 is the second most recent, and so on.
     * @return the value at the specified index in the past
     * @throws IndexOutOfBoundsException if the index is negative or greater than or equal
     *         to the current count
     */
    public double get(int index) {
        long count = getCount();
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Window Size: " + count);
        }
        int actualIndex = lastIndex - index;
        return values[(actualIndex < 0) ? actualIndex + windowSize : actualIndex];
    }

    /**
     * Returns the difference between the last (newest) and the second-to-last
     * value in the sliding window.
     *
     * <p>This method calculates the difference between the two most recently
     * added elements to the window. If the window contains one element, it
     * returns its value. If the window is empty, it returns 0.
     *
     * @return the difference between the last and second-to-last value,
     *         or the last value if only one element is present,
     *         or 0 if the window is empty
     */
    public double getLastDifference() {
        int secondLastIndex = (lastIndex == 0) ? windowSize - 1 : lastIndex - 1;
        return values[lastIndex] - values[secondLastIndex];
    }

    /**
     * Resets the statistics to their initial state.
     */
    public void reset() {
        lastIndex = windowSize - 1;
        sum = 0.0;
        sumCompensation = 0.0;
        simpleSum = 0.0;
        countTotal = 0;
        minDeque.clear();
        maxDeque.clear();
        // importantly, reset the values array
        Arrays.fill(values, 0.0);
    }

    /**
     * Returns a non-empty string representation of this object suitable for
     * debugging. The exact presentation format is unspecified and may vary
     * between implementations and versions.
     *
     * @return a string representation of this object
     */
    @Override
    public String toString() {
        return String.format(Locale.US,
                "%s{count=%d, sum=%f, min=%f, average=%f, max=%f}",
                this.getClass().getSimpleName(),
                getCount(),
                getSum(),
                getMin(),
                getAverage(),
                getMax());
    }
}
