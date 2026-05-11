package one.chartsy.charting;

/// Formats numeric chart values for presentation.
///
/// `ValueFormatter` is the label-formatting strategy behind two related extension points:
/// - [Scale#setLabelFormat(ValueFormatter)] replaces the scale's own
///   [StepsDefinition#computeLabel(double)] logic when tick labels and
///   [ScaleAnnotation] text are generated.
/// - [Chart#setXValueFormat(ValueFormatter)] and [Chart#setYValueFormat(int, ValueFormatter)]
///   override the values returned by [Chart#formatXValue(double)] and
///   [Chart#formatYValue(int, double)], which renderers and interaction helpers use for data
///   labels, pie-slice percentages, and hover text.
///
/// Because this formatter runs instead of the default [StepsDefinition] formatting path,
/// attaching one bypasses any locale-aware, category-aware, time-aware, or
/// transformer-aware label generation that the current steps definition would normally
/// provide. Implementations that still need those conventions must apply them explicitly.
///
/// Implementations should be cheap and free of visible side effects. The chart may call them
/// repeatedly while repainting scales, computing renderer labels, or updating interaction UI.
/// Returning `null` is passed through unchanged by the charting layer and may surface as
/// missing or literal `null` text depending on the caller.
///
/// If an implementation can be retained on a serializable [Chart] or [Scale], make that
/// implementation serializable as well.
@FunctionalInterface
public interface ValueFormatter {
    
    /// Converts a raw chart value into display text.
    ///
    /// The supplied number is the value from the chart model, before any label text is built by
    /// the current [StepsDefinition]. For transformed axes this means the formatter receives the
    /// original axis value and must apply any additional unit conversion or transformation it
    /// wants to expose in the label.
    ///
    /// @param value
    ///     the numeric value being rendered for an axis, annotation, or data-label context
    /// @return
    ///     a display-ready label; callers generally expect a non-`null` string
    String formatValue(double value);
}
