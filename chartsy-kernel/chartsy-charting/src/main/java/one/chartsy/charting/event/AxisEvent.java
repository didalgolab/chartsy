package one.chartsy.charting.event;

import java.util.EventObject;

import one.chartsy.charting.Axis;

/// Base event type for notifications emitted by an [Axis].
///
/// Every axis event carries the originating [Axis] together with the axis's current adjusting
/// state. Interactors set that flag while a drag, scroll, or zoom gesture is still in progress so
/// listeners can defer expensive work until the final non-adjusting event arrives.
///
/// [AxisChangeEvent] reports discrete state changes such as transformer updates, while
/// [AxisRangeEvent] carries before-and-after range snapshots.
public class AxisEvent extends EventObject {
    private final boolean adjusting;

    /// Creates an axis event that is not part of an in-progress adjustment.
    public AxisEvent(Axis axis) {
        this(axis, false);
    }

    /// Creates an axis event for `axis`.
    ///
    /// @param adjusting `true` when the axis is still inside a user-driven adjustment batch
    public AxisEvent(Axis axis, boolean adjusting) {
        super(axis);
        this.adjusting = adjusting;
    }

    /// Returns the axis that emitted this event.
    public Axis getAxis() {
        return (Axis) getSource();
    }

    /// Returns whether the originating axis was still inside an in-progress adjustment batch.
    public final boolean isAdjusting() {
        return adjusting;
    }
}
