package one.chartsy.charting;

import java.io.Serializable;
import java.util.Locale;

/// Defines how one [Scale] generates major ticks, optional sub-steps, and formatted labels.
///
/// A steps definition is the policy layer between an [Axis]' numeric visible range and the live
/// tick caches owned by `Scale.Steps`. `Scale` asks it to:
/// - optionally normalize the visible range before the axis is finalized,
/// - choose the previous and next major or sub-step positions around arbitrary values,
/// - describe whether a distinct sub-step sequence exists,
/// - format labels for prepared values, and
/// - refresh any locale-sensitive or scale-sensitive derived state in [#update()] before the next
///   tick-generation pass.
///
/// One definition instance is attached to at most one scale at a time through [#setScale(Scale)].
/// Implementations commonly keep small mutable caches derived from the owning scale or locale, so
/// they are not thread-safe.
public abstract class StepsDefinition implements Serializable {
    private Scale scale;
    private transient Locale locale;

    protected StepsDefinition() {
    }

    /// Lets the definition expand or snap `visibleRange` before the axis uses it.
    ///
    /// `dataRange` is the raw data range when available. Implementations may ignore it or mutate
    /// `visibleRange` in place.
    void adjustRange(DataInterval dataRange, DataInterval visibleRange) {
    }

    /// Returns the least major step that is not smaller than `value`.
    final double ceilingStep(double value) {
        double step = previousStep(value);
        return (step == value) ? step : incrementStep(step);
    }

    /// Records the scale that currently owns this definition.
    void setScale(Scale scale) {
        this.scale = scale;
    }

    /// Returns the least sub-step that is not smaller than `value`.
    final double ceilingSubStep(double value) {
        double subStep = previousSubStep(value);
        return (subStep == value) ? subStep : incrementSubStep(subStep);
    }

    /// Formats one prepared tick label.
    public abstract String computeLabel(double value);

    /// Returns whether tick generation should run in the axis transformer's visible-space domain.
    ///
    /// Definitions that return `true` receive transformed values in the `previous...` and
    /// `increment...` families. `Scale` converts the generated results back through the inverse
    /// transformer before it stores them in the prepared tick arrays.
    boolean usesTransformedVisibleRange() {
        return false;
    }

    /// Invalidates prepared tick values and layout on the attached scale.
    ///
    /// Definitions call this after configuration changes that can affect generated tick positions
    /// or label geometry.
    void refreshScale() {
        if (getScale() != null) {
            if (getScale().getSteps() != null)
                getScale().getSteps().invalidateValues();
            getScale().invalidateLayout();
        }
    }

    /// Returns the axis currently served by this definition, or `null` while detached.
    final Axis getAxis() {
        return (getScale() == null) ? null : getScale().getAxis();
    }

    /// Returns the current locale used for label formatting.
    ///
    /// The locale is resolved lazily from the owning chart and falls back to
    /// [Locale#getDefault()] while detached. When the resolved locale changes, [#localeChanged()]
    /// is invoked before the new locale is returned.
    public Locale getLocale() {
        Locale resolvedLocale = null;
        Scale scale = getScale();
        if (scale != null) {
            Chart chart = scale.getChart();
            if (chart != null)
                resolvedLocale = chart.getLocale();
        }
        if (resolvedLocale == null)
            resolvedLocale = Locale.getDefault();
        if (!resolvedLocale.equals(locale)) {
            locale = resolvedLocale;
            localeChanged();
        }
        return locale;
    }

    /// Returns the scale currently using this definition, or `null` while detached.
    public final Scale getScale() {
        return scale;
    }

    /// Returns the axis transformer currently applied to the owning axis, or `null` while absent.
    final AxisTransformer getAxisTransformer() {
        Axis axis = getAxis();
        return (axis == null) ? null : axis.getTransformer();
    }

    /// Returns whether tick generation should continue from `value`.
    ///
    /// Built-in definitions always return `true`. Custom definitions can override this to cap
    /// finite step sequences.
    public boolean hasNext(double value) {
        return true;
    }

    /// Returns whether this definition exposes a distinct sub-step sequence.
    public boolean hasSubStep() {
        return false;
    }

    /// Returns the next major step after `value`.
    public abstract double incrementStep(double value);

    /// Returns the next sub-step after `value`.
    ///
    /// The default implementation reports that sub-steps do not advance independently.
    public double incrementSubStep(double value) {
        return value;
    }

    /// Hook invoked after [#getLocale()] detects a locale change.
    ///
    /// Implementations typically refresh cached formatters or calendar helpers here.
    public void localeChanged() {
    }

    /// Returns the next major step relative to an arbitrary `value`.
    public double nextStep(double value) {
        return incrementStep(previousStep(value));
    }

    /// Returns the next sub-step relative to an arbitrary `value`.
    public double nextSubStep(double value) {
        return incrementSubStep(previousSubStep(value));
    }

    /// Returns the greatest major step not greater than `value`.
    public abstract double previousStep(double value);

    /// Returns the greatest sub-step not greater than `value`.
    ///
    /// The default implementation reports that sub-steps do not advance independently.
    public double previousSubStep(double value) {
        return value;
    }

    /// Refreshes any derived state needed for the next tick-generation pass.
    ///
    /// `Scale` calls this before it scans the current visible range and asks for major or minor
    /// steps.
    public void update() {
    }
}
