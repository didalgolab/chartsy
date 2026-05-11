package one.chartsy.charting;

/// Signals that an [AxisTransformer] cannot complete a forward or inverse value mapping.
///
/// [AxisTransformer#apply(double)] and [AxisTransformer#inverse(double)] declare this
/// checked exception so transformer implementations can reject values that fall outside their
/// supported domain, cannot be inverted, or would otherwise produce an unusable transformed value.
///
/// Callers typically handle this exception at chart projection, scale-label, and interaction
/// boundaries by abandoning the current conversion instead of rendering or navigating with partial
/// transformation results.
///
/// This type carries only a detail message. Built-in transformers in this module currently keep
/// the checked exception in the API contract even when they do not actively throw it for normal
/// inputs, which leaves custom transformers a dedicated failure channel for recoverable mapping
/// errors.
public class AxisTransformerException extends Exception {

    /// Creates an exception describing the transformation failure.
    ///
    /// @param message explains why the value or interval could not be transformed
    public AxisTransformerException(String message) {
        super(message);
    }
}
