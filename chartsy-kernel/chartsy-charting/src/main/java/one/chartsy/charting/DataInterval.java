package one.chartsy.charting;

import java.io.Serializable;

import one.chartsy.charting.util.MathUtil;

/// Represents a mutable inclusive numeric interval used throughout charting for axis ranges,
/// visible windows, and reusable out-parameters.
///
/// An interval is treated as empty whenever its bounds do not describe an ordered span. The
/// no-argument constructor and [#empty()] reset that state to the module's canonical sentinel
/// `min = 1.0` and `max = -1.0`, but callers should test emptiness with [#isEmpty()] rather than
/// comparing endpoints directly.
///
/// Many range-producing APIs in this module accept an existing `DataInterval` and mutate it in
/// place to avoid allocating a new object. Instances therefore remain intentionally mutable, expose
/// their endpoints directly, and are not thread-safe.
///
/// ### API Note
///
/// The `isBefore` and `isAfter` families describe the probe value's position relative to this
/// interval: `interval.isBefore(x)` reports that `x` is on or left of the low edge, while
/// `interval.isAfter(x)` reports that `x` is on or right of the high edge.
public final class DataInterval implements Cloneable, Serializable {

    /// Stores the inclusive lower endpoint of the current span.
    public double min;

    /// Stores the inclusive upper endpoint of the current span.
    public double max;

    /// Creates the module's canonical empty interval.
    public DataInterval() {
        this(1.0, -1.0);
    }

    /// Creates a mutable copy of another interval.
    ///
    /// @param interval the interval whose current endpoints should be copied
    public DataInterval(DataInterval interval) {
        this(interval.getMin(), interval.getMax());
    }

    /// Creates an interval with caller-specified endpoints.
    ///
    /// Passing bounds that do not describe an ordered span creates an empty interval.
    ///
    /// @param min the lower endpoint to store
    /// @param max the upper endpoint to store
    public DataInterval(double min, double max) {
        this.min = min;
        this.max = max;
    }

    /// Expands this interval so it covers every point from `interval`.
    ///
    /// Empty input intervals are ignored. When this interval is empty, the other interval's current
    /// endpoints are adopted directly.
    ///
    /// @param interval the interval whose endpoints should become included
    /// @return `true` when this call changes either endpoint
    public boolean add(DataInterval interval) {
        if (interval.isEmpty())
            return false;
        if (isEmpty()) {
            min = interval.getMin();
            max = interval.getMax();
            return true;
        }
        var changed = add(interval.getMin());
        return add(interval.getMax()) || changed;
    }

    /// Expands this interval so it covers `value`.
    ///
    /// @param value the value that must lie inside the resulting interval
    /// @return `true` when this call changes either endpoint
    public boolean add(double value) {
        if (isEmpty()) {
            min = value;
            max = value;
            return true;
        }
        if (value < min) {
            min = value;
            return true;
        }
        if (value <= max)
            return false;
        max = value;
        return true;
    }

    /// Clamps `value` into this interval's inclusive bounds.
    ///
    /// Empty intervals leave the input unchanged.
    ///
    /// @param value the value to clamp
    /// @return `value` projected into this interval, or the original value when the interval is
    ///     empty
    public double clamp(double value) {
        if (isEmpty())
            return value;
        return MathUtil.clamp(value, min, max);
    }

    /// Returns a distinct interval with the same current endpoints.
    ///
    /// The clone is shallow but independent because this type stores only primitive state.
    @Override
    public Object clone() {
        return new DataInterval(this);
    }

    /// Returns whether both endpoints of `interval` lie inside this interval's inclusive bounds.
    ///
    /// @param interval the interval whose current endpoints should be tested
    /// @return `true` when both endpoints fall inside this interval
    public boolean contains(DataInterval interval) {
        return isInside(interval.getMin()) && isInside(interval.getMax());
    }

    /// Resets this interval to the module's canonical empty sentinel.
    public void empty() {
        min = 1.0;
        max = -1.0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof DataInterval interval))
            return false;
        return min == interval.min && max == interval.max;
    }

    /// Moves both endpoints away from the current center by `delta`.
    ///
    /// Positive values widen the interval. Negative values contract it and may turn it empty.
    ///
    /// @param delta the amount to subtract from [#min] and add to [#max]
    public void expand(double delta) {
        if (!isEmpty()) {
            min -= delta;
            max += delta;
        }
    }

    /// Returns the span between the current endpoints.
    ///
    /// Empty intervals report `0.0`.
    ///
    /// @return the inclusive span width, or `0.0` when the interval is empty
    public double getLength() {
        return isEmpty() ? 0.0 : max - min;
    }

    /// Returns the stored upper endpoint.
    ///
    /// @return the current value of [#max]
    public double getMax() {
        return max;
    }

    /// Returns the arithmetic midpoint of the current interval.
    ///
    /// Empty intervals report `0.0`.
    ///
    /// @return the midpoint of [#min] and [#max], or `0.0` when the interval is empty
    public double getMiddle() {
        return isEmpty() ? 0.0 : (max + min) / 2.0;
    }

    /// Returns the stored lower endpoint.
    ///
    /// @return the current value of [#min]
    public double getMin() {
        return min;
    }

    /// Replaces this interval with its overlap with `interval`.
    ///
    /// Disjoint or empty input intervals reset this instance to the canonical empty sentinel.
    ///
    /// @param interval the interval to intersect with this one
    public void intersection(DataInterval interval) {
        if (interval.isEmpty() || isEmpty()) {
            empty();
            return;
        }
        min = Math.max(min, interval.getMin());
        max = Math.min(max, interval.getMax());
        if (isEmpty())
            empty();
    }

    /// Returns whether this interval and `interval` share at least one point.
    ///
    /// @param interval the interval to test against
    /// @return `true` when the two intervals overlap or touch
    public boolean intersects(DataInterval interval) {
        return !isEmpty()
                && !isStrictlyBefore(interval.getMax())
                && !isStrictlyAfter(interval.getMin());
    }

    /// Returns whether `value` lies on or beyond this interval's upper bound.
    ///
    /// @param value the probe value to compare with [#max]
    /// @return `true` when `value >= max`
    public boolean isAfter(double value) {
        return !isEmpty() && value >= max;
    }

    /// Returns whether `value` lies on or before this interval's lower bound.
    ///
    /// @param value the probe value to compare with [#min]
    /// @return `true` when `value <= min`
    public boolean isBefore(double value) {
        return !isEmpty() && value <= min;
    }

    /// Returns whether this interval is currently empty.
    ///
    /// Reversed bounds and unordered numeric input such as `NaN` both count as empty.
    ///
    /// @return `true` when this interval does not describe an ordered span
    public boolean isEmpty() {
        return !(max >= min);
    }

    /// Returns whether `value` lies within this interval's inclusive bounds.
    ///
    /// @param value the probe value to test
    /// @return `true` when `value` is between [#min] and [#max], inclusive
    public boolean isInside(double value) {
        return max >= min && value >= min && value <= max;
    }

    /// Returns whether `value` lies outside this interval's open interior.
    ///
    /// Boundary points count as outside.
    ///
    /// @param value the probe value to test
    /// @return `true` when `value` is not strictly inside the interval
    public boolean isOutside(double value) {
        return !(max < min) && !(value > min && value < max);
    }

    /// Returns whether `value` lies strictly beyond this interval's upper bound.
    ///
    /// @param value the probe value to compare with [#max]
    /// @return `true` when `value > max`
    public boolean isStrictlyAfter(double value) {
        return !isEmpty() && value > max;
    }

    /// Returns whether `value` lies strictly before this interval's lower bound.
    ///
    /// @param value the probe value to compare with [#min]
    /// @return `true` when `value < min`
    public boolean isStrictlyBefore(double value) {
        return !isEmpty() && value < min;
    }

    /// Returns whether `value` lies strictly inside this interval.
    ///
    /// @param value the probe value to test
    /// @return `true` when `value` lies between the endpoints without touching them
    public boolean isStrictlyInside(double value) {
        return !(max < min) && value > min && value < max;
    }

    /// Returns whether `value` lies strictly outside this interval.
    ///
    /// Boundary points do not count as strictly outside.
    ///
    /// @param value the probe value to test
    /// @return `true` when `value` lies entirely outside the closed interval
    public boolean isStrictlyOutside(double value) {
        return !(max < min) && !(value >= min && value <= max);
    }

    /// Replaces both endpoints without normalizing their order.
    ///
    /// Passing bounds that do not describe an ordered span leaves this interval empty.
    ///
    /// @param min the lower endpoint to store
    /// @param max the upper endpoint to store
    public void set(double min, double max) {
        this.min = min;
        this.max = max;
    }

    /// Replaces the stored upper endpoint.
    ///
    /// @param max the new value for [#max]
    public void setMax(double max) {
        this.max = max;
    }

    /// Replaces the stored lower endpoint.
    ///
    /// @param min the new value for [#min]
    public void setMin(double min) {
        this.min = min;
    }

    /// Removes `interval` when it overlaps one outer edge of this interval.
    ///
    /// If `interval` fully contains this interval, this instance becomes empty. If removing
    /// `interval` would split the current span into two disjoint fragments, this method leaves the
    /// interval unchanged because `DataInterval` can represent only one contiguous range.
    ///
    /// @param interval the interval to remove from this one
    public void subtract(DataInterval interval) {
        if (interval.isEmpty())
            return;
        if (interval.contains(this))
            empty();
        else if (intersects(interval)) {
            if (interval.getMin() <= min)
                min = interval.getMax();
            else if (interval.getMax() >= max)
                max = interval.getMin();
        }
    }

    @Override
    public String toString() {
        return isEmpty() ? "[EMPTY]" : "[" + min + "," + max + "]";
    }

    /// Shifts both endpoints by `delta`.
    ///
    /// Empty intervals remain empty, although the stored sentinel values are translated as well.
    ///
    /// @param delta the amount to add to both endpoints
    public void translate(double delta) {
        min += delta;
        max += delta;
    }
}
