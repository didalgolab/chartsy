package one.chartsy.charting;

import java.text.NumberFormat;

import one.chartsy.charting.util.text.NumberFormatFactory;

/// Supplies major and minor tick sequences for axes using [LogarithmicAxisTransformer].
///
/// Major ticks are exact powers of the installed logarithm base. Minor ticks stay inside the
/// current major interval by walking multiples of that power, so a base-10 scale produces
/// `2, 3, ..., 9` between successive decades.
///
/// [Scale#setLogarithmic(double)] installs this definition together with a
/// [LogarithmicAxisTransformer]. Attaching the definition through
/// [Scale#setStepsDefinition(StepsDefinition)] has the same effect as long as the target scale is
/// already connected to an axis.
///
/// Labels are formatted from the original source-space values, not from logarithmic exponents. If
/// no explicit [NumberFormat] is configured, this definition creates one on demand from
/// [NumberFormatFactory] using the current chart locale.
public class LogarithmicStepsDefinition extends StepsDefinition {
    private static final double ROUNDING_EPSILON = 1.0E-6;

    private NumberFormat numberFormat;

    /// Creates a logarithmic steps definition that formats labels from the current chart locale.
    public LogarithmicStepsDefinition() {
    }

    final LogarithmicAxisTransformer getLogarithmicTransformer() {
        return (LogarithmicAxisTransformer) super.getAxisTransformer();
    }

    private NumberFormat createLocaleNumberFormat() {
        return NumberFormatFactory.getInstance(getLocale());
    }

    /// Formats one major or minor tick label.
    ///
    /// The supplied value is the raw axis value that the caller will eventually project through the
    /// owning [LogarithmicAxisTransformer].
    @Override
    public String computeLabel(double value) {
        return getNumberFormat().format(value);
    }

    /// Returns the formatter currently used for tick labels.
    ///
    /// When [#setNumberFormat(NumberFormat)] has not supplied an explicit formatter, this method
    /// creates a locale-aware formatter for the owning chart's current locale. Explicit formatters
    /// are retained and reused as-is.
    public NumberFormat getNumberFormat() {
        return numberFormat == null ? createLocaleNumberFormat() : numberFormat;
    }

    /// Returns `true` because logarithmic scales always expose intermediate ticks inside each major
    /// interval.
    @Override
    public boolean hasSubStep() {
        return true;
    }

    /// Returns the next major tick after `value`.
    ///
    /// Major ticks are successive powers of the installed logarithm base. Callers are expected to
    /// pass a value previously returned by [#previousStep(double)] or [#incrementStep(double)].
    @Override
    public double incrementStep(double value) {
        return value * getLogarithmicTransformer().getLogBase();
    }

    /// Returns the next minor tick after `value`.
    ///
    /// Minor ticks advance by the size of the enclosing major interval, which makes base-10 scales
    /// walk `10, 20, ..., 90` inside one decade before the next major tick is reached.
    @Override
    public double incrementSubStep(double value) {
        return value + previousStep(value);
    }

    /// Returns the major tick that anchors `value` to its logarithmic interval.
    ///
    /// Positive values are rounded down to the nearest power of the installed base. Non-positive
    /// values fall back to `1.0`, which is the first positive power this definition can expose.
    /// A small epsilon keeps exact powers from slipping into the previous interval because of
    /// floating-point rounding.
    @Override
    public double previousStep(double value) {
        LogarithmicAxisTransformer transformer = getLogarithmicTransformer();
        double logBase = transformer.getLogBase();
        double exponent = 0.0;

        if (value > 0.0) {
            double epsilon = value > Double.MAX_VALUE - ROUNDING_EPSILON ? 0.0 : ROUNDING_EPSILON;
            exponent = Math.floor(Math.log(value) / Math.log(logBase) + epsilon);
        }
        return Math.pow(logBase, exponent);
    }

    /// Returns the greatest minor tick not greater than `value`.
    ///
    /// The result is a multiple of the current major interval size. When `value` is below the
    /// first positive major tick, this may yield `0.0`, which acts as the logarithmic floor before
    /// the sequence reaches `1.0`.
    @Override
    public double previousSubStep(double value) {
        double majorStep = previousStep(value);
        return Math.floor(value / majorStep) * majorStep;
    }

    /// Replaces the formatter used for tick labels.
    ///
    /// The supplied formatter is retained by reference and used for subsequent
    /// [#computeLabel(double)] calls until it is replaced again or cleared with `null`. Changing
    /// the formatter invalidates cached label layout when this definition is already attached to a
    /// [Scale].
    ///
    /// @param numberFormat explicit formatter to use, or `null` to fall back to the chart locale
    public void setNumberFormat(NumberFormat numberFormat) {
        if (this.numberFormat == numberFormat) {
            return;
        }
        this.numberFormat = numberFormat;
        refreshScale();
    }
}
