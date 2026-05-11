package one.chartsy.charting.internal;

import java.io.Serializable;

import javax.swing.BoundedRangeModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import one.chartsy.charting.Axis;
import one.chartsy.charting.Chart;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.event.AxisChangeEvent;
import one.chartsy.charting.event.AxisListener;
import one.chartsy.charting.event.AxisRangeEvent;

/// Keeps one Swing [BoundedRangeModel] synchronized with one chart [Axis].
///
/// [Chart] uses this adapter when scroll bars or other bounded-range widgets
/// should track the visible span of an axis. The model's minimum and maximum define the full
/// scrollable domain, while its value and extent represent the current visible range projected onto
/// that domain.
///
/// Synchronization is bidirectional: axis range changes push new value and extent into the model,
/// and model changes update the axis visible range. The connector suppresses feedback loops with an
/// internal reentrancy guard. Setting `reversed` flips the model direction relative to the axis,
/// which is useful for widgets such as vertical scroll bars whose natural increasing direction
/// differs from chart coordinates.
public class BoundedRangeModelConnector implements AxisListener, ChangeListener, Serializable {
    /// Connected Swing range model.
    private BoundedRangeModel model;

    /// Connected chart axis.
    private Axis axis;

    /// Whether model coordinates run in the opposite direction to axis coordinates.
    private boolean reversed;
    private transient boolean updating;
    private transient int cachedValue;
    private transient int cachedExtent;

    /// Creates and immediately connects an unreversed model-to-axis bridge.
    ///
    /// @param model bounded range model to synchronize
    /// @param axis  axis whose visible range should stay in sync with `model`
    public BoundedRangeModelConnector(BoundedRangeModel model, Axis axis) {
        this(model, axis, false);
    }

    /// Creates and immediately connects a model-to-axis bridge.
    ///
    /// @param model    bounded range model to synchronize
    /// @param axis     axis whose visible range should stay in sync with `model`
    /// @param reversed `true` to reverse the model direction relative to the axis
    public BoundedRangeModelConnector(BoundedRangeModel model, Axis axis, boolean reversed) {
        updating = false;
        cachedValue = 0;
        cachedExtent = 0;
        this.model = model;
        this.axis = axis;
        this.reversed = reversed;
        axis.addAxisListener(this);
        model.addChangeListener(this);
        updateModel();
    }

    /// Ignores non-range axis change events.
    @Override
    public void axisChanged(AxisChangeEvent event) {
    }

    /// Pushes the committed axis range back into the connected model.
    @Override
    public void axisRangeChanged(AxisRangeEvent event) {
        if (!updating)
            if (!event.isAboutToChangeEvent()) {
                updateModel();
            }
    }

    /// Replaces the current connection with a new model-axis pair.
    ///
    /// Passing `null` for either endpoint leaves this connector disconnected after any existing
    /// listeners are removed.
    ///
    /// @param model    new bounded range model to synchronize
    /// @param axis     new axis to synchronize
    /// @param reversed `true` to reverse the model direction relative to the axis
    public synchronized void connect(BoundedRangeModel model, Axis axis, boolean reversed) {
        disconnect();
        this.model = model;
        this.axis = axis;
        this.reversed = reversed;
        if (model != null)
            if (axis != null) {
                axis.addAxisListener(this);
                model.addChangeListener(this);
                updateModel();
            }
    }

    /// Removes the current axis and model listeners and clears both retained endpoints.
    public synchronized void disconnect() {
        if (model != null) {
            model.removeChangeListener(this);
            model = null;
        }
        if (axis != null) {
            axis.removeAxisListener(this);
            axis = null;
        }
    }

    /// Returns the currently connected axis, or `null` when disconnected.
    ///
    /// @return the connected axis, or `null` after [#disconnect()]
    public final Axis getAxis() {
        return axis;
    }

    /// Returns the currently connected bounded range model, or `null` when disconnected.
    ///
    /// @return the connected model, or `null` after [#disconnect()]
    public final BoundedRangeModel getModel() {
        return model;
    }

    /// Returns whether the model direction is reversed relative to the axis.
    ///
    /// @return `true` when increasing model values move toward lower axis values
    public final boolean isReversed() {
        return reversed;
    }

    /// Pushes a model value/extent change into the connected axis visible range.
    @Override
    public void stateChanged(ChangeEvent event) {
        if (updating)
            return;

        axis.setAdjusting(model.getValueIsAdjusting());
        if (model.getValue() == cachedValue)
            if (model.getExtent() == cachedExtent)
                return;

        cachedValue = model.getValue();
        cachedExtent = model.getExtent();

        DataInterval dataRange = axis.getDataRange();
        int modelSpan = model.getMaximum() - model.getMinimum();
        double relativeStart = (!reversed)
                ? (model.getValue() - model.getMinimum()) / (double) modelSpan
                : (model.getMaximum() - model.getValue() - model.getExtent()) / (double) modelSpan;
        double visibleMin = dataRange.getMin() + dataRange.getLength() * relativeStart;
        double visibleLength = (model.getExtent() == 0)
                ? axis.getVisibleRange().getLength()
                : model.getExtent() * dataRange.getLength() / modelSpan;
        try {
            updating = true;
            axis.setVisibleRange(visibleMin, visibleMin + visibleLength);
        } finally {
            updating = false;
        }
    }

    /// Pushes the current axis visible range into the connected model.
    ///
    /// The axis data range acts as the full model domain. The connected model therefore receives a
    /// value and extent that represent the current visible range as an integer interval inside that
    /// domain.
    protected void updateModel() {
        DataInterval dataRange = axis.getDataRange();
        DataInterval visibleRange = axis.getVisibleRange();
        int modelSpan = model.getMaximum() - model.getMinimum();
        double visibleExtent = visibleRange.getLength() / dataRange.getLength() * modelSpan;
        double visibleOffset = (visibleRange.getMin() - dataRange.getMin()) / dataRange.getLength();
        int modelValue = (!reversed)
                ? (int) Math.ceil(model.getMinimum() + visibleOffset * modelSpan)
                : (int) Math.ceil(model.getMaximum() - visibleExtent - visibleOffset * modelSpan);
        try {
            updating = true;
            model.setRangeProperties(modelValue, (int) visibleExtent, model.getMinimum(),
                    model.getMaximum(), model.getValueIsAdjusting());
            cachedValue = modelValue;
            cachedExtent = (int) visibleExtent;
        } finally {
            updating = false;
        }
    }
}
