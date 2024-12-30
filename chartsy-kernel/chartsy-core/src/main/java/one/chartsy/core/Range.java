/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core;

/**
 * Represents an immutable range of double values.
 * <p>
 * A range is defined by its minimum (min) and maximum (max) values and can be used to represent intervals
 * in various contexts such as numeric ranges, time periods, etc. The class provides methods to create,
 * query, and manipulate ranges.
 * <p>
 * The {@link Range#EMPTY} constant defines an empty range where the max is less than the min.
 * <p>
 * This class is thread-safe since it is immutable.
 */
public final class Range {
    /**
     * A constant representing an empty range.
     */
    public static final Range EMPTY = new Range(0.5, -0.5);

    private final double min;
    private final double max;

    private Range(double min, double max) {
        this.min = min;
        this.max = max;
    }

    /**
     * Returns the empty range singleton instance.
     *
     * @return the empty range
     */
    public static Range empty() {
        return EMPTY;
    }

    /**
     * Creates a range with the same min and max values.
     *
     * @param val the value for both min and max
     * @return a new range with specified value
     */
    public static Range of(double val) {
        return create(val, val);
    }

    /**
     * Creates a range with specified min and max values.
     *
     * @param min the minimum value of the range
     * @param max the maximum value of the range
     * @return a new range with specified min and max, or the empty range if max is less than min
     */
    public static Range of(double min, double max) {
        return create(min, max);
    }

    /**
     * Creates a range from two values by determining the min and max.
     *
     * @param v1 one endpoint of the range
     * @param v2 another endpoint of the range
     * @return a new range with the min being the lesser of the two values and max the greater
     */
    public static Range fromValues(double v1, double v2) {
        return create(Math.min(v1, v2), Math.max(v1, v2));
    }

    /**
     * Creates a range that encompasses all the provided values.
     *
     * @param values an array of double values
     * @return a new range covering all the values, or the empty range if no values are provided
     */
    public static Range fromValues(double... values) {
        var rangeBuilder = new Builder();
        for (var value : values)
            rangeBuilder.add(value);

        return rangeBuilder.toRange();
    }

    private static Range create(double min, double max) {
        if (max < min)
            return EMPTY;
        return new Range(min, max);
    }

    /**
     * Returns the minimum value of the range.
     *
     * @return the min value
     */
    public double min() {
        return min;
    }

    /**
     * Returns the maximum value of the range.
     *
     * @return the max value
     */
    public double max() {
        return max;
    }

    /**
     * Calculates the mean (average) of the range's endpoints.
     *
     * @return the mean value
     */
    public double mean() {
        return (max + min)/2.0;
    }

    /**
     * Calculates the length of the range.
     *
     * @return the length, which is the difference between max and min
     */
    public double length() {
        return max - min;
    }

    /**
     * Checks if the range is empty (max < min).
     *
     * @return {@code true} if the range is empty, {@code false} otherwise
     */
    public boolean isEmpty() {
        return max < min;
    }

    /**
     * Checks if the range contains a specified value.
     *
     * @param value the value to check
     * @return {@code true} if the range is not empty and the value is within the range,
     *         {@code false} otherwise
     */
    public boolean contains(double value) {
        return !isEmpty() && value >= min && value <= max;
    }

    /**
     * Checks if the range fully contains another range.
     *
     * @param range the range to check
     * @return {@code true} if this range is not empty and completely contains the other range,
     *         {@code false} otherwise
     */
    public boolean contains(Range range) {
        return contains(range.min()) && contains(range.max());
    }

    /**
     * Checks if the range ends at or before a specified value.
     *
     * @param v the value to check against the range's max
     * @return {@code true} if the range is not empty and ends at or before the specified value,
     *         {@code false} otherwise
     */
    public boolean endsAtOrBefore(double v) {
        return !isEmpty() && !(v < max());
    }

    /**
     * Checks if the range starts at or after a specified value.
     *
     * @param v the value to check against the range's min
     * @return {@code true} if the range is not empty and starts at or after the specified value,
     *         {@code false} otherwise
     */
    public boolean startsAtOrAfter(double v) {
        return !isEmpty() && !(v > min());
    }

    /**
     * Calculates the intersection of this range with another range.
     *
     * @param range the range to intersect with
     * @return the intersection of the two ranges, which is the range itself if the other range
     *         is empty or contains this range
     */
    public Range intersection(Range range) {
        if (range.isEmpty() || contains(range))
            return range;
        if (isEmpty() || range.contains(this))
            return this;

        var min = Math.max(min(), range.min());
        var max = Math.min(max(), range.max());
        return create(min, max);
    }

    /**
     * Clips a value to the boundaries of the range.
     *
     * @param v the value to clip
     * @return the clipped value, which is the value itself if the range is empty
     */
    public double clip(double v) {
        if (isEmpty())
            return v;
        return Math.max(min(), Math.min(max(), v));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o instanceof Range) {
            Range that = (Range) o;
            return Double.compare(min, that.min) == 0 && Double.compare(max, that.max) == 0;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(min)*31 + Double.hashCode(max);
    }

    @Override
    public String toString() {
        return "[" + min() + ", " + max() + ']';
    }

    /**
     * Creates a new range with the specified minimum value while retaining the current maximum value.
     *
     * @param min the new minimum value for the range
     * @return a new {@link Range} instance with the updated minimum value, unless the value is unchanged
     */
    public Range withMin(double min) {
        if (min == this.min)
            return this;
        return create(min, max);
    }

    /**
     * Creates a new range with the specified maximum value while retaining the current minimum value.
     *
     * @param max the new maximum value for the range
     * @return a new {@link Range} instance with the updated maximum value, unless the value is unchanged
     */
    public Range withMax(double max) {
        if (max == this.max)
            return this;
        return create(min, max);
    }

    /**
     * Creates a new range with the specified minimum and maximum values.
     *
     * @param min the new minimum value for the range
     * @param max the new maximum value for the range
     * @return a new {@link Range} instance with the updated values, unless both values are unchanged
     */
    public Range withMinMax(double min, double max) {
        if (min == this.min && max == this.max)
            return this;
        return create(min, max);
    }

    /**
     * Extends the range to include the specified value. If the range is empty, a new range
     * containing only the specified value is returned.
     *
     * @param v the value to include in the range
     * @return a new {@link Range} instance extended to include the value, or the current
     *         range if the value is already included
     */
    public Range union(double v) {
        if (isEmpty())
            return Range.of(v);
        else if (v < min())
            return withMin(v);
        else if (v > max())
            return withMax(v);
        else
            return this;
    }

    /**
     * Extends the range to encompass another range. If either range is empty,
     * the non-empty range is returned.
     *
     * @param range the range to include
     * @return a new {@link Range} instance extended to include the other range
     */
    public Range union(Range range) {
        if (range.isEmpty())
            return this;
        else if (isEmpty())
            return range;
        else {
            var min = Math.min(min(), range.min());
            var max = Math.max(max(), range.max());
            return withMinMax(min, max);
        }
    }

    /**
     * Translates the range by a specified distance.
     *
     * @param dx the distance to translate the range by
     * @return a new {@link Range} instance translated by the specified distance
     */
    public Range translate(double dx) {
        if (dx == 0.0)
            return this;
        return create(min() + dx, max() + dx);
    }

    /**
     * Expands the range by a given distance in both directions.
     * Returns this range if the distance is zero or the range is empty.
     *
     * @param dx the expansion distance on each side
     * @return an expanded {@link Range} or this range if no expansion is needed
     */
    public Range expand(double dx) {
        if (dx == 0.0 || isEmpty())
            return this;
        return create(min() - dx, max() + dx);
    }

    /**
     * Builder class for constructing {@link Range} instances.
     */
    public static class Builder {
        private double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;

        /**
         * Adds a value to the range being built.
         *
         * @param val the value to add
         * @return the builder instance for chaining
         */
        public Builder add(double val) {
            return add(val, val);
        }

        /**
         * Adds a {@code Range} to the range being built.
         *
         * @param r the range to add
         * @return the builder instance for chaining
         */
        public Builder add(Range r) {
            return add(r.min(), r.max());
        }

        /**
         * Adds a range defined by min and max values to the range being built.
         *
         * @param min the minimum value to add
         * @param max the maximum value to add
         * @return the builder instance for chaining
         */
        public Builder add(double min, double max) {
            this.min = Math.min(this.min, min);
            this.max = Math.max(this.max, max);
            return this;
        }

        /**
         * Constructs a {@link Range} from the values added to the builder.
         *
         * @return a new {@link Range} instance
         */
        public Range toRange() {
            return create(min, max);
        }
    }
}
