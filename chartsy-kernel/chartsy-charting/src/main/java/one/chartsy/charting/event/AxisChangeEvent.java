package one.chartsy.charting.event;

import one.chartsy.charting.Axis;

/// Reports a discrete non-range change on an [Axis].
///
/// Unlike [AxisRangeEvent], this event does not carry interval snapshots. Callers inspect
/// [#getType()] to distinguish adjusting-state transitions, transformer reconfiguration, and
/// orientation flips that require repaint or scale invalidation.
public class AxisChangeEvent extends AxisEvent {
    /// Event type fired when [Axis#setAdjusting(boolean)] toggles.
    public static final int ADJUSTMENT_CHANGE = 1;

    /// Event type fired when the installed transformer reports an internal configuration change.
    public static final int TRANSFORMER_CHANGE = 2;

    /// Event type fired when axis orientation used for projection changes.
    public static final int ORIENTATION_CHANGE = 3;

    private final int type;

    /// Creates a non-adjusting axis-change event of `type`.
    public AxisChangeEvent(Axis axis, int type) {
        this(axis, type, false);
    }

    /// Creates an axis-change event of `type`.
    ///
    /// @param type one of [#ADJUSTMENT_CHANGE], [#TRANSFORMER_CHANGE], or [#ORIENTATION_CHANGE]
    /// @param adjusting `true` when the originating axis is still inside an in-progress adjustment
    public AxisChangeEvent(Axis axis, int type, boolean adjusting) {
        super(axis, adjusting);
        this.type = type;
    }

    /// Returns the encoded change kind.
    public final int getType() {
        return type;
    }

    /// Returns whether this event marks the end of an adjusting sequence.
    ///
    /// This is equivalent to `getType() == ADJUSTMENT_CHANGE && !isAdjusting()`.
    public final boolean isAdjustmentEnd() {
        return type == ADJUSTMENT_CHANGE && !isAdjusting();
    }
}
