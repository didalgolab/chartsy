package one.chartsy.charting.event;

import one.chartsy.charting.Axis;
import one.chartsy.charting.DataInterval;

/// Describes either the start or completion of a data-range or visible-range change on an [Axis].
///
/// `Axis` emits these events in pairs around each committed interval update. The same accessors
/// keep their meaning across both phases: [#getOldMin()], [#getOldMax()], [#getNewMin()], and
/// [#getNewMax()] always resolve the pre-change and post-change bounds regardless of whether this
/// instance represents the leading or trailing callback.
///
/// During [#isAboutToChangeEvent()], [#getStoredRange()] exposes the mutable interval object the
/// axis is about to apply. Listeners can revise that pending interval through [#setNewMin(double)]
/// and [#setNewMax(double)]. During [#isChangedEvent()], the same stored interval instead holds
/// the previous bounds so callers can still inspect what changed after the axis has been updated.
public class AxisRangeEvent extends AxisEvent {
    private final DataInterval storedRange;
    private final boolean aboutToChange;
    private final boolean visibleRangeEvent;

    /// Creates a range event for `axis`.
    ///
    /// `storedRange` carries the pending interval for a before-change callback or the previous
    /// interval for an after-change callback.
    ///
    /// @param aboutToChange `true` for the leading callback fired before the axis commits the new
    ///     interval, `false` for the trailing callback fired after the commit
    /// @param adjusting `true` when the axis is still inside an in-progress adjustment batch
    /// @param visibleRangeEvent `true` when the visible window changed, `false` when the data
    ///     range changed
    public AxisRangeEvent(Axis axis, DataInterval storedRange, boolean aboutToChange,
                          boolean adjusting, boolean visibleRangeEvent) {
        super(axis, adjusting);
        this.storedRange = storedRange;
        this.aboutToChange = aboutToChange;
        this.visibleRangeEvent = visibleRangeEvent;
    }

    /// Returns the upper bound that will be in effect after this change.
    public double getNewMax() {
        if (!isChangedEvent())
            return storedRange.getMax();
        return getCurrentRangeMax();
    }

    /// Returns the lower bound that will be in effect after this change.
    public double getNewMin() {
        if (!isChangedEvent())
            return storedRange.getMin();
        return getCurrentRangeMin();
    }

    /// Returns the upper bound that was in effect before this change.
    public double getOldMax() {
        if (!isAboutToChangeEvent())
            return storedRange.getMax();
        return getCurrentRangeMax();
    }

    /// Returns the lower bound that was in effect before this change.
    public double getOldMin() {
        if (!isAboutToChangeEvent())
            return storedRange.getMin();
        return getCurrentRangeMin();
    }

    /// Returns the interval object retained by this event.
    ///
    /// Before the axis commits the change this is the pending interval that may still be adjusted.
    /// After the commit it is the previous interval preserved for old/new comparisons.
    public DataInterval getStoredRange() {
        return storedRange;
    }

    /// Returns whether this is the leading callback fired before the axis commits the new bounds.
    public final boolean isAboutToChangeEvent() {
        return aboutToChange;
    }

    /// Returns whether this is the trailing callback fired after the axis committed the new
    /// bounds.
    public final boolean isChangedEvent() {
        return !aboutToChange;
    }

    /// Returns whether the change translates the whole interval without changing its span.
    ///
    /// This is `true` only when both endpoints move by the same non-zero signed delta.
    public final boolean isScrollEvent() {
        double minDelta = getNewMin() - getOldMin();
        double maxDelta = getNewMax() - getOldMax();
        return Double.compare(minDelta, 0.0) != 0 && Double.compare(minDelta, maxDelta) == 0;
    }

    /// Returns whether this event describes the visible window instead of the full data range.
    public final boolean isVisibleRangeEvent() {
        return visibleRangeEvent;
    }

    /// Replaces the pending upper bound for a leading before-change callback.
    ///
    /// Changed events ignore this request because the axis has already committed the interval.
    public void setNewMax(double max) {
        if (isAboutToChangeEvent())
            storedRange.setMax(max);
    }

    /// Replaces the pending lower bound for a leading before-change callback.
    ///
    /// Changed events ignore this request because the axis has already committed the interval.
    public void setNewMin(double min) {
        if (isAboutToChangeEvent())
            storedRange.setMin(min);
    }

    private double getCurrentRangeMax() {
        return isVisibleRangeEvent() ? getAxis().getVisibleMax() : getAxis().getDataMax();
    }

    private double getCurrentRangeMin() {
        return isVisibleRangeEvent() ? getAxis().getVisibleMin() : getAxis().getDataMin();
    }
}
