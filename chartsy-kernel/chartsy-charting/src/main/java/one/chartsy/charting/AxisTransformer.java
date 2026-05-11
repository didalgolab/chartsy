package one.chartsy.charting;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.Serializable;

/// Defines a bidirectional mapping that an [Axis] applies to its numeric coordinate space.
///
/// An axis keeps its data and visible ranges in source coordinates. When a transformer is
/// installed, chart projection code uses [#apply(double)] and [#apply(double[], int)] to move
/// those values into the transformed axis space before display mapping, while interaction and tick
/// generation code use [#inverse(double)] and [#inverse(double[], int)] to recover source
/// coordinates. [Axis] also calls [#validateInterval(DataInterval)] while normalizing data and
/// visible ranges so a transformer can clamp, round, or expand intervals to a supported domain.
///
/// This base class supplies the property-change channel that mutable transformers use to notify
/// their owning axis about configuration changes such as new scaling factors or zoom windows.
/// Current charting code typically treats transformers as mutable UI-state objects and mutates
/// them from chart interaction paths rather than sharing them across threads.
public abstract class AxisTransformer implements Serializable {
    private final PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);

    /// Creates a transformer base with ready-to-use property-change support.
    protected AxisTransformer() {
    }

    /// Registers a listener for every mapping-related property change published by this
    /// transformer.
    ///
    /// [Axis] uses this channel to invalidate cached geometry and labels when a mutable
    /// transformer changes after installation.
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changeSupport.addPropertyChangeListener(listener);
    }

    /// Registers a listener for one subclass-defined property name.
    ///
    /// Use this overload when only a specific mapping parameter matters, for example a scaling
    /// factor or zoom range.
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        changeSupport.addPropertyChangeListener(propertyName, listener);
    }

    /// Transforms both bounds of `interval` in place.
    ///
    /// The transformed bounds are stored back in ascending order, so decreasing mappings such as
    /// a negative affine scale still produce a well-formed interval object.
    ///
    /// @param interval the source-space interval to mutate
    /// @return the same `interval` instance after transformation
    /// @throws AxisTransformerException if either interval bound cannot be transformed
    public DataInterval apply(DataInterval interval) throws AxisTransformerException {
        double transformedMin = apply(interval.getMin());
        double transformedMax = apply(interval.getMax());

        if (transformedMin <= transformedMax) {
            interval.set(transformedMin, transformedMax);
        } else {
            interval.set(transformedMax, transformedMin);
        }
        return interval;
    }

    /// Transforms one source-space value into this transformer's axis space.
    ///
    /// Callers rely on this method for individual data-point conversion and as the scalar building
    /// block for the interval and array helpers in this base class.
    ///
    /// @param value the source-space value to transform
    /// @return the transformed value
    /// @throws AxisTransformerException if the value cannot be represented in the transformed space
    public abstract double apply(double value) throws AxisTransformerException;

    /// Transforms the first `count` entries of `values` in place.
    ///
    /// The default implementation delegates to [#apply(double)] for each entry. Subclasses may
    /// override it when batch conversion can preserve additional semantics or avoid repeated scalar
    /// dispatch.
    ///
    /// @param values the array whose leading entries should be transformed
    /// @param count  how many elements from index `0` onward to transform
    /// @return the same `values` array after transformation
    /// @throws AxisTransformerException if any processed element cannot be transformed
    public double[] apply(double[] values, int count) throws AxisTransformerException {
        for (int index = 0; index < count; index++) {
            values[index] = apply(values[index]);
        }
        return values;
    }

    /// Publishes a mapping-related property change to registered listeners.
    ///
    /// Subclasses should call this after mutating any property that affects forward conversion,
    /// inverse conversion, or interval normalization.
    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        changeSupport.firePropertyChange(propertyName, oldValue, newValue);
    }

    /// Transforms both bounds of `interval` from transformed space back into source space.
    ///
    /// The transformed bounds are stored back in ascending order so callers keep a valid source
    /// interval even when the mapping reverses direction.
    ///
    /// @param interval the transformed-space interval to mutate
    /// @return the same `interval` instance after inverse transformation
    /// @throws AxisTransformerException if either interval bound cannot be inverted
    public DataInterval inverse(DataInterval interval) throws AxisTransformerException {
        double sourceMin = inverse(interval.getMin());
        double sourceMax = inverse(interval.getMax());

        if (sourceMin <= sourceMax) {
            interval.set(sourceMin, sourceMax);
        } else {
            interval.set(sourceMax, sourceMin);
        }
        return interval;
    }

    /// Inverts one transformed-space value back into source coordinates.
    ///
    /// Scale generation and interaction code rely on this method when a transformed axis still
    /// needs to expose values in the axis's original coordinate system.
    ///
    /// @param value the transformed-space value to invert
    /// @return the corresponding source-space value
    /// @throws AxisTransformerException if the value cannot be inverted
    public abstract double inverse(double value) throws AxisTransformerException;

    /// Inverts the first `count` entries of `values` in place.
    ///
    /// The default implementation delegates to [#inverse(double)] for each entry.
    ///
    /// @param values the array whose leading entries should be inverse-transformed
    /// @param count  how many elements from index `0` onward to invert
    /// @return the same `values` array after inverse transformation
    /// @throws AxisTransformerException if any processed element cannot be inverted
    public double[] inverse(double[] values, int count) throws AxisTransformerException {
        for (int index = 0; index < count; index++) {
            values[index] = inverse(values[index]);
        }
        return values;
    }

    /// Unregisters a listener that was previously added to this transformer.
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changeSupport.removePropertyChangeListener(listener);
    }

    /// Adjusts `interval` to a range that this transformer can represent.
    ///
    /// [Axis] calls this hook while normalizing data and visible ranges. Implementations may clamp
    /// illegal bounds, round them to preferred step boundaries, or enlarge the interval to keep
    /// transformer-specific state visible. Return `true` only when the supplied interval was
    /// changed.
    ///
    /// @param interval the axis-space interval to validate and possibly mutate
    /// @return `true` if the interval was modified
    public boolean validateInterval(DataInterval interval) {
        return false;
    }
}
