/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core;

public final class Range {
    public static final Range EMPTY = new Range(0.5, -0.5);

    private final double min;
    private final double max;

    private Range(double min, double max) {
        this.min = min;
        this.max = max;
    }

    public static Range empty() {
        return EMPTY;
    }

    public static Range of(double val) {
        return create(val, val);
    }

    public static Range of(double min, double max) {
        return create(min, max);
    }

    public static Range fromValues(double v1, double v2) {
        return create(Math.min(v1, v2), Math.max(v1, v2));
    }

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

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getMiddle() {
        return (max + min)/2.0;
    }

    public double getLength() {
        return max - min;
    }

    public boolean isEmpty() {
        return max < min;
    }

    public boolean contains(double value) {
        return !isEmpty() && value >= min && value <= max;
    }

    public boolean contains(Range range) {
        return contains(range.getMin()) && contains(range.getMax());
    }

    public boolean endsAtOrBefore(double v) {
        return !isEmpty() && !(v < getMax());
    }

    public boolean startsAtOrAfter(double v) {
        return !isEmpty() && !(v > getMin());
    }

    public Range intersection(Range range) {
        if (range.isEmpty() || contains(range))
            return range;
        if (isEmpty() || range.contains(this))
            return this;

        var min = Math.max(getMin(), range.getMin());
        var max = Math.min(getMax(), range.getMax());
        return create(min, max);
    }

    public double clip(double v) {
        if (isEmpty())
            return v;
        return Math.max(getMin(), Math.min(getMax(), v));
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
        return '[' + getMin() + ", " + getMax() + ']';
    }

    public Range withMin(double min) {
        if (min == this.min)
            return this;
        return create(min, max);
    }

    public Range withMax(double max) {
        if (max == this.max)
            return this;
        return create(min, max);
    }

    public Range withMinMax(double min, double max) {
        if (min == this.min && max == this.max)
            return this;
        return create(min, max);
    }

    public Range union(double v) {
        if (isEmpty())
            return Range.of(v);
        else if (v < getMin())
            return withMin(v);
        else if (v > getMax())
            return withMax(v);
        else
            return this;
    }

    public Range union(Range range) {
        if (range.isEmpty())
            return this;
        else if (isEmpty())
            return range;
        else {
            var min = Math.min(getMin(), range.getMin());
            var max = Math.max(getMax(), range.getMax());
            return withMinMax(min, max);
        }
    }

    public Range translate(double dx) {
        if (dx == 0.0)
            return this;
        return create(getMin() + dx, getMax() + dx);
    }

    public Range expand(double dx) {
        if (dx == 0.0 || isEmpty())
            return this;
        return create(getMin() - dx, getMax() + dx);
    }

    public static class Builder {
        private double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;

        public Builder add(double val) {
            return add(val, val);
        }

        public Builder add(Range r) {
            return add(r.getMin(), r.getMax());
        }

        public Builder add(double min, double max) {
            this.min = Math.min(this.min, min);
            this.max = Math.max(this.max, max);
            return this;
        }

        public Range toRange() {
            return create(min, max);
        }
    }
}
