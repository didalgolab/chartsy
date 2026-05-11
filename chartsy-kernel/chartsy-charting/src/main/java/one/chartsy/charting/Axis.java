package one.chartsy.charting;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;

import javax.swing.event.EventListenerList;

import one.chartsy.charting.event.AxisChangeEvent;
import one.chartsy.charting.event.AxisListener;
import one.chartsy.charting.event.AxisRangeEvent;
import one.chartsy.charting.util.Flags;

/// Represents one numeric axis in a [Chart] coordinate system.
///
/// An axis tracks two related source-space intervals:
/// - the data range, which bounds the values that renderers currently make available
/// - the visible range, which is the window currently projected on screen
///
/// The visible range is always stored in ascending order and, when the axis is bounded, clamped
/// back into the data range. Callers can let either data bound follow renderer output through the
/// auto-range flags or pin explicit bounds with the `set...Range(...)` methods.
///
/// Installed [AxisTransformer] instances do not replace the stored source-space ranges. They are
/// consulted when new ranges are normalized and when projection code asks for
/// [#getTVisibleRange()].
///
/// Inside the charting package an axis may delegate its range state to another axis of the same
/// type. Delegated axes proxy range queries and mutations to the root axis, but keep their own
/// listener lists so attached UI elements can mirror the root without sharing one listener
/// registry.
///
/// Axes are mutable UI model objects and are not thread-safe.
public class Axis implements Serializable {

    /// Crossing implementation that always resolves to one fixed axis value.
    static class AnchoredCrossing implements Axis.Crossing, Serializable {
        private final double value;

        AnchoredCrossing(double value) {
            this.value = value;
        }

        @Override
        public double getValue(Axis axis) {
            return value;
        }
    }

    /// Resolves the position on the opposite axis where this axis should cross it.
    public interface Crossing {

        /// Returns the coordinate on `axis` where the paired axis should cross.
        ///
        /// The supplied axis is usually the perpendicular axis from a [CoordinateSystem].
        double getValue(Axis axis);
    }

    /// Mirrors delegate events back into the dependent axis's own listener list.
    private static class DelegateNotifier implements AxisListener, Serializable {
        private final Axis axis;

        private boolean savedAdjusting;

        DelegateNotifier(Axis axis) {
            this.axis = axis;
        }

        @Override
        public void axisChanged(AxisChangeEvent event) {
            axis.fireAxisChanged(event.getType());
        }

        @Override
        public void axisRangeChanged(AxisRangeEvent event) {
            axis.fireAxisRangeChanged(event.getStoredRange(), event.isAboutToChangeEvent(),
                    event.isVisibleRangeEvent());
        }
    }

    /// Base class for stateless crossings derived from the current visible range.
    static abstract class FixedCrossing implements Axis.Crossing, Serializable {
    }

    /// Crossing strategy that resolves to the current visible maximum of the supplied axis.
    static class MaxCrossing extends Axis.FixedCrossing {

        private MaxCrossing() {
        }

        @Override
        public double getValue(Axis axis) {
            return axis.getVisibleMax();
        }
    }

    /// Crossing strategy that resolves to the current visible minimum of the supplied axis.
    static class MinCrossing extends Axis.FixedCrossing {

        private MinCrossing() {
        }

        @Override
        public double getValue(Axis axis) {
            return axis.getVisibleMin();
        }
    }

    /// Keeps two standalone axes in step by copying range and adjusting events in both directions.
    private static class Synchronizer implements AxisListener, Serializable {
        private final Axis firstAxis;
        private final Axis secondAxis;
        private boolean updating;

        private Synchronizer(Axis firstAxis, Axis secondAxis) {
            this.firstAxis = firstAxis;
            this.secondAxis = secondAxis;
        }

        private static void connect(Axis sourceAxis, Axis targetAxis) {
            Synchronizer sourceSynchronizer = find(sourceAxis, targetAxis);
            Synchronizer targetSynchronizer = find(targetAxis, sourceAxis);
            if (sourceSynchronizer != null && targetSynchronizer != null)
                return;
            if (sourceSynchronizer != null)
                sourceAxis.removeAxisListener(sourceSynchronizer);
            if (targetSynchronizer != null)
                targetAxis.removeAxisListener(targetSynchronizer);
            DataInterval sourceDataRange = sourceAxis.getDataRange();
            if (!sourceDataRange.isEmpty())
                targetAxis.setDataRange(sourceDataRange);
            DataInterval sourceVisibleRange = sourceAxis.getVisibleRange();
            if (!sourceVisibleRange.isEmpty())
                targetAxis.setVisibleRange(sourceVisibleRange);
            Synchronizer synchronizer = new Synchronizer(sourceAxis, targetAxis);
            sourceAxis.addAxisListener(synchronizer);
            targetAxis.addAxisListener(synchronizer);
        }

        private static Synchronizer find(Axis axis, Axis counterpart) {
            Object[] listeners = axis.listenerList.getListenerList();
            int index = listeners.length - 1;
            while (index >= 0) {
                if (listeners[index] instanceof Synchronizer synchronizer
                        && (synchronizer.firstAxis == counterpart
                        || synchronizer.secondAxis == counterpart))
                    return synchronizer;
                index -= 2;
            }
            return null;
        }

        private static void disconnect(Axis firstAxis, Axis secondAxis) {
            Synchronizer firstSynchronizer = find(firstAxis, secondAxis);
            Synchronizer secondSynchronizer = find(secondAxis, firstAxis);
            if (firstSynchronizer != null)
                firstAxis.removeAxisListener(firstSynchronizer);
            if (secondSynchronizer != null)
                secondAxis.removeAxisListener(secondSynchronizer);
        }

        @Override
        public synchronized void axisChanged(AxisChangeEvent event) {
            if (!updating)
                if (event.getType() == AxisChangeEvent.ADJUSTMENT_CHANGE) {
                    Axis mirroredAxis = event.getAxis() != firstAxis ? firstAxis : secondAxis;
                    try {
                        updating = true;
                        mirroredAxis.setAdjusting(event.isAdjusting());
                    } finally {
                        updating = false;
                    }
                }
        }

        @Override
        public synchronized void axisRangeChanged(AxisRangeEvent event) {
            if (!updating)
                if (!event.isAboutToChangeEvent()) {
                    try {
                        Axis mirroredAxis = event.getAxis() != firstAxis ? firstAxis : secondAxis;
                        updating = true;
                        double newMin = event.getNewMin();
                        double newMax = event.getNewMax();
                        if (newMin <= newMax)
                            if (!event.isVisibleRangeEvent())
                                mirroredAxis.setDataRange(newMin, newMax);
                            else
                                mirroredAxis.setVisibleRange(newMin, newMax);
                    } finally {
                        updating = false;
                    }
                }
        }
    }

    /// Crossing strategy that resolves to the current visible maximum of the supplied axis.
    public static final Axis.Crossing MAX_VALUE = new Axis.MaxCrossing();

    /// Crossing strategy that resolves to the current visible minimum of the supplied axis.
    public static final Axis.Crossing MIN_VALUE = new Axis.MinCrossing();

    /// Constant returned by [#getType()] for x axes.
    public static final int X_AXIS = 1;

    /// Constant returned by [#getType()] for y axes.
    public static final int Y_AXIS = 2;

    private static final int FLAG_AUTO_DATA_MAX = 1;
    private static final int FLAG_AUTO_DATA_MIN = 2;
    private static final int FLAG_AUTO_VISIBLE_RANGE = 4;
    private static final int FLAG_ADJUSTING = 32;
    private static final int FLAG_REVERSED = 64;
    private static final int NORMALIZATION_PASS_LIMIT = 5;
    private static final double UNBOUNDED_MIN = -Double.MAX_VALUE;
    private static final double UNBOUNDED_MAX = Double.MAX_VALUE;

    private int type;
    private final Flags flags;
    private final EventListenerList listenerList;
    private Axis delegate;
    private double requestedVisibleMin;
    private double requestedVisibleMax;
    private double effectiveVisibleStart;
    private double effectiveVisibleEnd;
    private DataInterval visibleRange;
    private double requestedDataMin;
    private double requestedDataMax;
    private double effectiveDataStart;
    private double effectiveDataEnd;
    private DataInterval dataRange;
    private AxisTransformer transformer;

    private Chart chart;

    private final PropertyChangeListener axisTransformerChangeListener = this::axisTransformerChanged;

    Axis() {
        flags = new Flags(FLAG_AUTO_DATA_MAX | FLAG_AUTO_DATA_MIN | FLAG_AUTO_VISIBLE_RANGE);
        listenerList = new EventListenerList();
        requestedVisibleMin = 1.0;
        requestedVisibleMax = -1.0;
        effectiveVisibleStart = requestedVisibleMin;
        effectiveVisibleEnd = requestedVisibleMax;
        visibleRange = new DataInterval(effectiveVisibleStart, effectiveVisibleEnd);
        requestedDataMin = UNBOUNDED_MIN;
        requestedDataMax = UNBOUNDED_MAX;
        effectiveDataStart = requestedDataMin;
        effectiveDataEnd = requestedDataMax;
        dataRange = new DataInterval(effectiveDataStart, effectiveDataEnd);
    }

    Axis(int type) {
        this();
        this.type = type;
    }

    /// Internal chart hook that makes this axis proxy another axis of the same orientation.
    ///
    /// The delegate chain must stay acyclic. When delegation is active, range getters and setters
    /// forward to the root axis while this axis republishes the root's change events through its own
    /// listener list.
    void setDelegate(Axis delegateAxis) {
        if (delegateAxis == delegate)
            return;
        if (delegateAxis != null) {
            if (delegateAxis.getType() != type)
                throw new RuntimeException("Axis: delegate has different type");
            for (Axis candidate = delegateAxis; candidate != null; candidate = candidate.delegate)
                if (candidate == this)
                    throw new IllegalArgumentException("Axis: cannot create circular delegation loop");
        }

        DelegateNotifier notifier = null;
        if (delegate != null) {
            Object[] listeners = delegate.listenerList.getListenerList();
            for (int index = listeners.length - 1; index >= 0; index -= 2) {
                if (listeners[index] instanceof Axis.DelegateNotifier candidate && candidate.axis == this) {
                    notifier = candidate;
                    break;
                }
            }
            if (notifier == null)
                throw new RuntimeException("Axis: delegate lacking a DelegateNotifier");
            delegate.removeAxisListener(notifier);
            setAdjusting(notifier.savedAdjusting);
        }

        delegate = delegateAxis;
        if (delegate != null) {
            boolean adjusting = isAdjusting();
            if (delegate.isAdjusting())
                setAdjusting(true);
            if (notifier == null)
                notifier = new Axis.DelegateNotifier(this);
            notifier.savedAdjusting = adjusting;
            delegate.addAxisListener(notifier);
        }
    }

    /// Links this axis back to its owning [Chart] so auto-range changes can request recomputation.
    void setChart(Chart chart) {
        this.chart = chart;
    }

    /// Internal chart hook that applies a freshly computed auto data range.
    ///
    /// Explicit caller-pinned minimum or maximum values are preserved when only one side of the
    /// range is still automatic.
    final boolean updateAutoDataRange(DataInterval computedRange) {
        double dataMin = isAutoDataMin() ? computedRange.getMin() : effectiveDataStart;
        double dataMax = isAutoDataMax() ? computedRange.getMax() : effectiveDataEnd;
        return applyDataRange(dataMin, dataMax);
    }

    private void fireAxisRangeChanged(DataInterval storedRange, boolean aboutToChange,
                                      boolean visibleRangeEvent) {
        if (listenerList.getListenerCount() == 0)
            return;
        Object[] listeners = listenerList.getListenerList();
        AxisRangeEvent event = new AxisRangeEvent(this, storedRange, aboutToChange, isAdjusting(),
                visibleRangeEvent);
        for (int index = listeners.length - 1; index >= 0; index -= 2)
            ((AxisListener) listeners[index]).axisRangeChanged(event);
    }

    private double clampTranslationToBounds(double delta) {
        if (delta > 0.0)
            if (getDataMax() >= getVisibleMax())
                return Math.min(getDataMax() - getVisibleMax(), delta);
        if (delta < 0.0)
            if (getDataMin() <= getVisibleMin())
                return Math.max(getDataMin() - getVisibleMin(), delta);
        return 0.0;
    }

    private boolean applyDataRange(double min, double max) {
        boolean reversedRange = min > max;
        DataInterval normalizedRange = reversedRange ? new DataInterval(max, min)
                : new DataInterval(min, max);
        if (getTransformer() != null)
            getTransformer().validateInterval(normalizedRange);
        if (!reversedRange) {
            effectiveDataStart = normalizedRange.getMin();
            effectiveDataEnd = normalizedRange.getMax();
        } else {
            effectiveDataEnd = normalizedRange.getMin();
            effectiveDataStart = normalizedRange.getMax();
        }
        if (dataRange.equals(normalizedRange))
            return false;
        fireAxisRangeChanged(normalizedRange, true, false);
        DataInterval previousRange = dataRange;
        dataRange = normalizedRange;
        fireAxisRangeChanged(previousRange, false, false);
        if (flags.getFlag(FLAG_AUTO_VISIBLE_RANGE) && resetVisibleRangeToDataRange())
            return true;
        applyVisibleRange(requestedVisibleMin, requestedVisibleMax, false);
        return true;
    }

    private boolean applyVisibleRange(double min, double max, boolean ignoreDuplicateRequest) {
        if (ignoreDuplicateRequest && min == effectiveVisibleStart && max == effectiveVisibleEnd)
            return false;
        boolean reversedRange = min > max;
        DataInterval normalizedRange = reversedRange ? new DataInterval(max, min)
                : new DataInterval(min, max);
        normalizeVisibleRange(normalizedRange);
        if (!normalizedRange.isEmpty() && !Double.isNaN(normalizedRange.getLength())) {
            if (normalizedRange.equals(visibleRange)) {
                if (!reversedRange) {
                    effectiveVisibleStart = normalizedRange.getMin();
                    effectiveVisibleEnd = normalizedRange.getMax();
                } else {
                    effectiveVisibleEnd = normalizedRange.getMin();
                    effectiveVisibleStart = normalizedRange.getMax();
                }
                return false;
            }
            fireAxisRangeChanged(normalizedRange, true, true);
            normalizeVisibleRange(normalizedRange);
            DataInterval previousRange = visibleRange;
            if (!reversedRange) {
                effectiveVisibleStart = normalizedRange.getMin();
                effectiveVisibleEnd = normalizedRange.getMax();
            } else {
                effectiveVisibleEnd = normalizedRange.getMin();
                effectiveVisibleStart = normalizedRange.getMax();
            }
            visibleRange = normalizedRange;
            fireAxisRangeChanged(previousRange, false, true);
            return true;
        }
        return false;
    }

    /// Internal chart hook used when a [Chart] attaches the axis as x or y.
    void setType(int type) {
        this.type = type;
    }

    /// Registers a listener for axis-orientation and range change events.
    ///
    /// Range listeners receive paired before/after events around each committed visible-range or
    /// data-range change.
    public synchronized void addAxisListener(AxisListener listener) {
        listenerList.add(AxisListener.class, listener);
    }

    private void axisTransformerChanged(PropertyChangeEvent event) {
        if (delegate != null)
            delegate.axisTransformerChanged(event);
        else {
            fireAxisChanged(AxisChangeEvent.TRANSFORMER_CHANGE);
            revalidateVisibleRange();
        }
    }

    /// Resets local range configuration back to the default automatic state.
    final void reset() {
        if (delegate != null)
            delegate.reset();
        else {
            setTransformer(null);
            setReversed(false);
            setAutoVisibleRange(true);
            setAutoDataRange(true);
        }
    }

    private void normalizeVisibleRange(DataInterval range) {
        for (int pass = 0; pass < NORMALIZATION_PASS_LIMIT; pass++) {
            boolean transformerAdjustedRange = transformer != null && transformer.validateInterval(range);
            boolean rangeAdjusted = clampToDataRange(range) || transformerAdjustedRange;
            if (!rangeAdjusted)
                return;
        }
    }

    private void fireAxisChanged(int changeType) {
        Object[] listeners = listenerList.getListenerList();
        if (listeners.length == 0)
            return;
        AxisChangeEvent event = new AxisChangeEvent(this, changeType, isAdjusting());
        for (int index = listeners.length - 1; index >= 0; index -= 2)
            ((AxisListener) listeners[index]).axisChanged(event);
    }

    /// Returns whether this axis currently proxies another axis's range state.
    boolean hasDelegate() {
        return delegate != null;
    }

    private boolean clampToDataRange(DataInterval range) {
        if (dataRange.contains(range))
            return false;
        range.intersection(dataRange);
        if (range.isEmpty())
            if (range.getMin() <= getDataMax())
                range.set(getDataMin(), getDataMin());
            else
                range.set(getDataMax(), getDataMax());
        return true;
    }

    private boolean resetVisibleRangeToDataRange() {
        if (!isBounded())
            return false;
        applyVisibleRange(dataRange.getMin(), dataRange.getMax(), true);
        return true;
    }

    private void revalidateVisibleRange() {
        applyVisibleRange(effectiveVisibleStart, effectiveVisibleEnd, false);
    }

    /// Returns the effective upper bound of the data range currently exposed by this axis.
    ///
    /// On a delegated axis this mirrors the root axis rather than any local requested value.
    public final double getDataMax() {
        if (delegate != null)
            return delegate.getDataMax();
        return dataRange.getMax();
    }

    /// Returns the effective lower bound of the data range currently exposed by this axis.
    ///
    /// On a delegated axis this mirrors the root axis rather than any local requested value.
    public final double getDataMin() {
        if (delegate != null)
            return delegate.getDataMin();
        return dataRange.getMin();
    }

    /// Returns a snapshot of the current data range.
    ///
    /// The returned [DataInterval] is a copy, so mutating it does not change this axis.
    public DataInterval getDataRange() {
        if (delegate != null)
            return delegate.getDataRange();
        return new DataInterval(dataRange);
    }

    /// Returns the explicit upper data bound most recently requested by the caller.
    ///
    /// This is the raw requested value, not the effective upper bound after transformer validation
    /// or range normalization.
    public final double getSpecifiedDataMax() {
        if (delegate == null)
            return requestedDataMax;
        return delegate.getSpecifiedDataMax();
    }

    /// Returns the explicit lower data bound most recently requested by the caller.
    ///
    /// This is the raw requested value, not the effective lower bound after transformer validation
    /// or range normalization.
    public final double getSpecifiedDataMin() {
        if (delegate == null)
            return requestedDataMin;
        return delegate.getSpecifiedDataMin();
    }

    /// Returns the explicit upper visible bound most recently requested by the caller.
    ///
    /// This may differ from the effective visible range when the axis had to clamp the request to
    /// its data range or when an [AxisTransformer] adjusted the interval.
    public final double getSpecifiedVisibleMax() {
        if (delegate == null)
            return requestedVisibleMax;
        return delegate.getSpecifiedVisibleMax();
    }

    /// Returns the explicit lower visible bound most recently requested by the caller.
    ///
    /// This may differ from the effective visible range when the axis had to clamp the request to
    /// its data range or when an [AxisTransformer] adjusted the interval.
    public final double getSpecifiedVisibleMin() {
        if (delegate == null)
            return requestedVisibleMin;
        return delegate.getSpecifiedVisibleMin();
    }

    /// Returns the transformer currently consulted for interval validation and transformed-range
    /// queries.
    public final AxisTransformer getTransformer() {
        if (delegate == null)
            return transformer;
        return delegate.getTransformer();
    }

    /// Returns the current visible range after applying the installed [AxisTransformer].
    ///
    /// Projectors and transformed step definitions use this view for screen mapping. If the
    /// transformer rejects either bound, this method returns an empty [DataInterval] instead of
    /// propagating the failure.
    public DataInterval getTVisibleRange() {
        if (delegate != null)
            return delegate.getTVisibleRange();
        DataInterval transformedVisibleRange = getVisibleRange();
        if (transformer != null)
            try {
                transformer.apply(transformedVisibleRange);
            } catch (AxisTransformerException ignored) {
                transformedVisibleRange = new DataInterval();
            }
        return transformedVisibleRange;
    }

    /// Returns the orientation constant that determines how charts and projectors interpret this
    /// axis.
    ///
    /// Compare the result with `X_AXIS` and `Y_AXIS`.
    public final int getType() {
        return type;
    }

    /// Returns the effective upper bound of the visible window.
    ///
    /// On a delegated axis this mirrors the root axis rather than any local requested value.
    public final double getVisibleMax() {
        if (delegate != null)
            return delegate.getVisibleMax();
        return visibleRange.getMax();
    }

    /// Returns the effective lower bound of the visible window.
    ///
    /// On a delegated axis this mirrors the root axis rather than any local requested value.
    public final double getVisibleMin() {
        if (delegate != null)
            return delegate.getVisibleMin();
        return visibleRange.getMin();
    }

    /// Returns a snapshot of the current visible range.
    ///
    /// The returned [DataInterval] is a copy, so mutating it does not change this axis.
    public final DataInterval getVisibleRange() {
        if (delegate != null)
            return delegate.getVisibleRange();
        return new DataInterval(visibleRange);
    }

    /// Returns whether the axis is in the middle of a user-driven adjustment.
    ///
    /// Interactors toggle this flag around drag, scroll, and zoom gestures so listeners can defer
    /// expensive work until the matching `false` transition arrives.
    public final boolean isAdjusting() {
        return flags.getFlag(FLAG_ADJUSTING);
    }

    /// Returns whether the upper data bound currently follows chart-computed data.
    public final boolean isAutoDataMax() {
        if (delegate != null)
            return delegate.isAutoDataMax();
        return flags.getFlag(FLAG_AUTO_DATA_MAX);
    }

    /// Returns whether the lower data bound currently follows chart-computed data.
    public final boolean isAutoDataMin() {
        if (delegate != null)
            return delegate.isAutoDataMin();
        return flags.getFlag(FLAG_AUTO_DATA_MIN);
    }

    /// Returns whether either data bound is currently tracked automatically.
    public final boolean isAutoDataRange() {
        if (delegate != null)
            return delegate.isAutoDataRange();
        return isAutoDataMin() || isAutoDataMax();
    }

    /// Returns whether the visible range currently follows the data range automatically.
    public final boolean isAutoVisibleRange() {
        if (delegate != null)
            return delegate.isAutoVisibleRange();
        return flags.getFlag(FLAG_AUTO_VISIBLE_RANGE);
    }

    /// Returns whether the data range has been reduced from the module's unbounded sentinels.
    public final boolean isBounded() {
        if (delegate != null)
            return delegate.isBounded();
        return dataRange.getMin() != UNBOUNDED_MIN || dataRange.getMax() != UNBOUNDED_MAX;
    }

    /// Returns whether projection should run in the reversed direction.
    ///
    /// Reversal affects how projectors interpret the stored ranges; it does not swap the stored
    /// source-space endpoints themselves.
    public final boolean isReversed() {
        if (delegate != null)
            return delegate.isReversed();
        return flags.getFlag(FLAG_REVERSED);
    }

    /// Returns whether [#getType()] currently reports `X_AXIS`.
    public final boolean isXAxis() {
        return type == X_AXIS;
    }

    /// Returns whether [#getType()] currently reports `Y_AXIS`.
    public final boolean isYAxis() {
        return type == Y_AXIS;
    }

    /// Unregisters a listener that was previously added with [#addAxisListener(AxisListener)].
    public synchronized void removeAxisListener(AxisListener listener) {
        listenerList.remove(AxisListener.class, listener);
    }

    /// Marks the axis as entering or leaving an in-progress user adjustment.
    ///
    /// This fires `AxisChangeEvent.ADJUSTMENT_CHANGE` when the flag changes.
    public final void setAdjusting(boolean adjusting) {
        if (adjusting != isAdjusting()) {
            flags.setFlag(FLAG_ADJUSTING, adjusting);
            fireAxisChanged(AxisChangeEvent.ADJUSTMENT_CHANGE);
        }
    }

    /// Turns automatic tracking of the upper data bound on or off.
    ///
    /// Re-enabling it on an axis that belongs to a [Chart] immediately asks the chart to
    /// recompute the data range for this axis.
    public void setAutoDataMax(boolean autoDataMax) {
        if (delegate != null)
            delegate.setAutoDataMax(autoDataMax);
        else if (autoDataMax != isAutoDataMax()) {
            flags.setFlag(FLAG_AUTO_DATA_MAX, autoDataMax);
            if (autoDataMax && chart != null)
                chart.updateAutoDataRange(this);
        }
    }

    /// Turns automatic tracking of the lower data bound on or off.
    ///
    /// Re-enabling it on an axis that belongs to a [Chart] immediately asks the chart to
    /// recompute the data range for this axis.
    public void setAutoDataMin(boolean autoDataMin) {
        if (delegate != null)
            delegate.setAutoDataMin(autoDataMin);
        else if (autoDataMin != isAutoDataMin()) {
            flags.setFlag(FLAG_AUTO_DATA_MIN, autoDataMin);
            if (autoDataMin && chart != null)
                chart.updateAutoDataRange(this);
        }
    }

    /// Turns automatic tracking of both data bounds on or off.
    ///
    /// Re-enabling it on an axis that belongs to a [Chart] immediately asks the chart to
    /// recompute the data range for this axis.
    public void setAutoDataRange(boolean autoDataRange) {
        if (delegate != null)
            delegate.setAutoDataRange(autoDataRange);
        else {
            boolean wasAutoDataRange = isAutoDataRange();
            if (autoDataRange != wasAutoDataRange) {
                flags.setFlag(FLAG_AUTO_DATA_MIN, autoDataRange);
                flags.setFlag(FLAG_AUTO_DATA_MAX, autoDataRange);
                if (autoDataRange && chart != null)
                    chart.updateAutoDataRange(this);
            }
        }
    }

    /// Turns automatic tracking of the visible range on or off.
    ///
    /// When enabled on a bounded axis, the visible range snaps back to the current data range.
    /// When the data range is still unbounded, the flag is remembered and the current visible range
    /// is left unchanged until a bounded data range becomes available.
    public void setAutoVisibleRange(boolean autoVisibleRange) {
        if (delegate != null)
            delegate.setAutoVisibleRange(autoVisibleRange);
        else if (autoVisibleRange != isAutoVisibleRange()) {
            flags.setFlag(FLAG_AUTO_VISIBLE_RANGE, autoVisibleRange);
            if (autoVisibleRange)
                resetVisibleRangeToDataRange();
        }
    }

    /// Pins the upper data bound to `dataMax`.
    ///
    /// This disables automatic tracking of the upper bound. The effective range may still be
    /// adjusted by the installed [AxisTransformer].
    public void setDataMax(double dataMax) {
        if (delegate != null)
            delegate.setDataMax(dataMax);
        else {
            requestedDataMax = dataMax;
            flags.setFlag(FLAG_AUTO_DATA_MAX, false);
            applyDataRange(effectiveDataStart, dataMax);
        }
    }

    /// Pins the lower data bound to `dataMin`.
    ///
    /// This disables automatic tracking of the lower bound. The effective range may still be
    /// adjusted by the installed [AxisTransformer].
    public void setDataMin(double dataMin) {
        if (delegate != null)
            delegate.setDataMin(dataMin);
        else {
            requestedDataMin = dataMin;
            flags.setFlag(FLAG_AUTO_DATA_MIN, false);
            applyDataRange(dataMin, effectiveDataEnd);
        }
    }

    /// Pins both data bounds from `range`.
    ///
    /// This is equivalent to calling [#setDataRange(double, double)] with the interval endpoints.
    public void setDataRange(DataInterval range) {
        setDataRange(range.getMin(), range.getMax());
    }

    /// Pins both data bounds explicitly.
    ///
    /// The requested interval is normalized into ascending order and then offered to the installed
    /// [AxisTransformer] for validation. If automatic visible ranging is enabled, the visible range
    /// is recomputed from the new data range after the update.
    public void setDataRange(double min, double max) {
        if (delegate != null)
            delegate.setDataRange(min, max);
        else {
            requestedDataMin = min;
            requestedDataMax = max;
            flags.setFlag(FLAG_AUTO_DATA_MIN, false);
            flags.setFlag(FLAG_AUTO_DATA_MAX, false);
            applyDataRange(min, max);
        }
    }

    /// Flips projection direction without changing the stored source-space range values.
    public void setReversed(boolean reversed) {
        if (delegate != null)
            delegate.setReversed(reversed);
        else if (reversed != isReversed()) {
            flags.setFlag(FLAG_REVERSED, reversed);
            fireAxisChanged(AxisChangeEvent.ORIENTATION_CHANGE);
        }
    }

    /// Installs the transformer consulted by range normalization and transformed-range queries.
    ///
    /// Mutable transformers should publish property changes after internal reconfiguration so the
    /// owning axis can revalidate its visible range and invalidate dependent UI.
    public void setTransformer(AxisTransformer transformer) {
        if (delegate != null)
            delegate.setTransformer(transformer);
        else if (transformer != this.transformer) {
            if (this.transformer != null)
                this.transformer.removePropertyChangeListener(axisTransformerChangeListener);

            if (transformer != null)
                transformer.addPropertyChangeListener(axisTransformerChangeListener);
            this.transformer = transformer;
        }
    }

    /// Pins the upper visible bound explicitly.
    ///
    /// This disables automatic visible ranging. The effective visible range may still be clamped to
    /// the data range or adjusted by the installed [AxisTransformer].
    public void setVisibleMax(double visibleMax) {
        if (delegate != null)
            delegate.setVisibleMax(visibleMax);
        else {
            requestedVisibleMax = visibleMax;
            if (visibleMax == effectiveVisibleEnd)
                return;
            flags.setFlag(FLAG_AUTO_VISIBLE_RANGE, false);
            applyVisibleRange(effectiveVisibleStart, visibleMax, true);
        }
    }

    /// Pins the lower visible bound explicitly.
    ///
    /// This disables automatic visible ranging. The effective visible range may still be clamped to
    /// the data range or adjusted by the installed [AxisTransformer].
    public void setVisibleMin(double visibleMin) {
        if (delegate != null)
            delegate.setVisibleMin(visibleMin);
        else {
            requestedVisibleMin = visibleMin;
            if (visibleMin == effectiveVisibleStart)
                return;
            flags.setFlag(FLAG_AUTO_VISIBLE_RANGE, false);
            applyVisibleRange(visibleMin, effectiveVisibleEnd, true);
        }
    }

    /// Pins the visible window from `range`.
    ///
    /// This is equivalent to calling [#setVisibleRange(double, double)] with the interval endpoints.
    public void setVisibleRange(DataInterval range) {
        setVisibleRange(range.getMin(), range.getMax());
    }

    /// Pins the visible window explicitly.
    ///
    /// The requested interval is normalized into ascending order, constrained to the current data
    /// range, and then revalidated through the installed [AxisTransformer]. The raw requested
    /// endpoints remain available through [#getSpecifiedVisibleMin()] and
    /// [#getSpecifiedVisibleMax()].
    public void setVisibleRange(double min, double max) {
        if (delegate != null)
            delegate.setVisibleRange(min, max);
        else {
            requestedVisibleMin = min;
            requestedVisibleMax = max;
            if (min == effectiveVisibleStart && max == effectiveVisibleEnd)
                return;
            flags.setFlag(FLAG_AUTO_VISIBLE_RANGE, false);
            applyVisibleRange(min, max, true);
        }
    }

    /// Starts or stops bidirectional synchronization with `axis`.
    ///
    /// When synchronization is enabled, this axis first copies `axis`'s current non-empty data and
    /// visible ranges and then mirrors subsequent data-range, visible-range, and adjusting changes
    /// through paired listeners.
    ///
    /// @throws IllegalArgumentException if `axis` is `null` or this axis itself
    public void synchronizeWith(Axis axis, boolean synchronize) {
        if (axis != this)
            if (axis != null) {
                if (synchronize)
                    Axis.Synchronizer.connect(axis, this);
                else
                    Axis.Synchronizer.disconnect(axis, this);
                return;
            }
        throw new IllegalArgumentException("Invalid axis");
    }

    @Override
    public String toString() {
        return getClass().getName() + (isXAxis() ? " X" : " Y") + " Visi: " + visibleRange
                + " Data: " + dataRange;
    }

    /// Shifts the visible range by `delta` in source coordinates.
    ///
    /// When the axis is bounded, the shift is clamped so the resulting visible range stays inside
    /// the data range.
    public void translateVisibleRange(double delta) {
        if (delegate != null)
            delegate.translateVisibleRange(delta);
        else {
            double effectiveDelta = clampTranslationToBounds(delta);
            if (effectiveDelta != 0.0)
                setVisibleRange(getVisibleMin() + effectiveDelta, getVisibleMax() + effectiveDelta);
        }
    }
}
