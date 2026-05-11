package one.chartsy.charting.event;

import java.beans.PropertyChangeEvent;

import one.chartsy.charting.LabelRenderer;
import one.chartsy.charting.ScaleAnnotation;
import one.chartsy.charting.graphic.DataIndicator;
import one.chartsy.charting.graphic.LabelDecoration;

/// Property-change event emitted by [LabelRenderer] with extra repaint-versus-layout flags.
///
/// [LabelRenderer#firePropertyChange(String, Object, Object, int)] publishes this subtype instead
/// of a plain [PropertyChangeEvent] so owners such as [ScaleAnnotation], [DataIndicator], and
/// [LabelDecoration] can distinguish drawing-only updates from changes that also invalidate cached
/// bounds.
public class LabelRendererPropertyEvent extends PropertyChangeEvent {
    /// Effect flag indicating that the rendered label appearance can change.
    public static final int AFFECTS_DRAWING = 1;

    /// Effect flag indicating that measured bounds or placement can change.
    public static final int AFFECTS_BOUNDS = 2;

    /// Raw effect flags attached to this event.
    private final int effects;

    /// Creates one renderer property-change event with explicit effect flags.
    ///
    /// @param source       renderer whose state changed
    /// @param propertyName property name reported to listeners
    /// @param oldValue     previous property value
    /// @param newValue     current property value
    /// @param effects      bitmask composed from [#AFFECTS_DRAWING] and [#AFFECTS_BOUNDS]
    public LabelRendererPropertyEvent(LabelRenderer source, String propertyName, Object oldValue, Object newValue, int effects) {
        super(source, propertyName, oldValue, newValue);
        this.effects = effects;
    }

    /// Returns whether the change can alter painted output.
    ///
    /// @return `true` when listeners should repaint to reflect the new renderer state
    public boolean affectsDrawing() {
        return (effects & AFFECTS_DRAWING) != 0;
    }

    /// Returns whether the change can alter measured bounds or placement.
    ///
    /// Despite the historical method name, this flag is used for any geometry-affecting change,
    /// not just literal size changes.
    ///
    /// @return `true` when listeners should invalidate cached geometry or layout
    public boolean affectsSizes() {
        return (effects & AFFECTS_BOUNDS) != 0;
    }

    /// Returns the raw effect bitmask attached to this event.
    ///
    /// @return bitmask composed from [#AFFECTS_DRAWING] and [#AFFECTS_BOUNDS]
    public int getEffects() {
        return effects;
    }

    /// Returns the [LabelRenderer] that published this event.
    ///
    /// @return event source cast to [LabelRenderer]
    public LabelRenderer getLabelRenderer() {
        return (LabelRenderer) super.getSource();
    }
}
