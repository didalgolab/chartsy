package one.chartsy.charting.event;

import java.util.EventListener;

/// Receives non-structural change notifications from an axis.
///
/// Listeners see two complementary event streams. [#axisChanged(AxisChangeEvent)] reports
/// discrete state changes such as adjusting toggles, transformer updates, and orientation flips.
/// [#axisRangeChanged(AxisRangeEvent)] wraps data-range and visible-range updates with paired
/// before/after callbacks so listeners can distinguish a pending change from one that is already
/// committed.
///
/// Implementations that cache expensive derived state typically watch the range callback for the
/// final changed event where [AxisRangeEvent#isChangedEvent()] is `true` and
/// [AxisRangeEvent#isAdjusting()] is `false`.
public interface AxisListener extends EventListener {

    /// Handles a discrete axis state change that does not carry interval endpoints.
    void axisChanged(AxisChangeEvent event);

    /// Handles an impending or committed axis interval change.
    ///
    /// During the leading [AxisRangeEvent#isAboutToChangeEvent()] callback, listeners can inspect
    /// or adjust the pending interval. During the trailing changed callback, the axis already
    /// exposes the committed range through its normal getters.
    void axisRangeChanged(AxisRangeEvent event);
}
