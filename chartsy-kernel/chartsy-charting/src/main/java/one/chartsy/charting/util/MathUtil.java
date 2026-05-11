package one.chartsy.charting.util;

/// Provides scalar helpers for chart geometry code that works in degrees rather than radians.
///
/// The trigonometric helpers normalize full turns and return exact cardinal-direction values so
/// label placement, tick generation, and bounding-box calculations can branch on horizontal and
/// vertical cases without carrying tiny floating-point remnants. The same utility also centralizes
/// the tolerance used when projector code needs to treat a visible span as effectively zero.
///
/// All methods are side-effect free.
public final class MathUtil {
    /// Absolute tolerance used when chart projection code treats a span as effectively zero.
    public static final double EPSILON = 1.0E-10;

    private static final double FULL_CIRCLE_DEGREES = 360.0;
    private static final double RIGHT_ANGLE_DEGREES = 90.0;
    private static final double STRAIGHT_ANGLE_DEGREES = 180.0;
    private static final double THREE_QUARTER_TURN_DEGREES = 270.0;
    private static final double DEGREES_PER_RADIAN = 180.0 / Math.PI;
    private static final double RADIANS_PER_DEGREE = Math.PI / STRAIGHT_ANGLE_DEGREES;

    private MathUtil() {
    }

    /// Constrains a value to the closed interval described by the supplied bounds.
    ///
    /// Axis-range and percentage calculations use this helper to cap intermediate values without
    /// changing the original scale.
    ///
    /// @param value value to clamp
    /// @param min   inclusive lower bound
    /// @param max   inclusive upper bound
    /// @return `value` when it already lies inside the interval, otherwise the nearest bound
    public static double clamp(double value, double min, double max) {
        if (value > max)
            return max;
        if (value < min)
            return min;
        return value;
    }

    /// Computes the cosine of an angle expressed in degrees.
    ///
    /// Any whole-turn-equivalent cardinal direction returns an exact canonical result, which keeps
    /// downstream layout code from mistaking a horizontal or vertical direction for a tiny
    /// non-zero slope after conversion noise.
    ///
    /// @param degrees angle in degrees
    /// @return the cosine of `degrees`
    public static double cosDeg(double degrees) {
        double normalizedDegrees = mod360(degrees);
        if (normalizedDegrees == 0.0)
            return 1.0;
        if (normalizedDegrees == RIGHT_ANGLE_DEGREES || normalizedDegrees == THREE_QUARTER_TURN_DEGREES)
            return 0.0;
        if (normalizedDegrees == STRAIGHT_ANGLE_DEGREES)
            return -1.0;
        return Math.cos(toRadians(normalizedDegrees));
    }

    /// Returns whether two values differ by less than a caller-supplied relative tolerance.
    ///
    /// The tolerance is scaled by the larger absolute operand, which makes this helper suitable
    /// for step-generation and projection code whose magnitudes depend on the currently visible
    /// range. It is not an absolute near-zero test.
    ///
    /// @param left              first value
    /// @param right             second value
    /// @param relativeTolerance fraction of the larger operand magnitude that still counts as equal
    /// @return `true` when the relative difference is smaller than `relativeTolerance`
    public static boolean equalsWithinRelativeTolerance(double left, double right, double relativeTolerance) {
        double difference = Math.abs(right - left);
        double threshold = Math.max(Math.abs(left), Math.abs(right)) * relativeTolerance;
        return !(difference >= threshold);
    }

    /// Returns whether a value lies strictly inside `(-EPSILON, EPSILON)`.
    ///
    /// Projector code uses this guard before dividing by visible spans that may collapse to a
    /// single point.
    ///
    /// @param value value to test
    /// @return `true` when `value` is strictly between `-EPSILON` and `EPSILON`
    public static boolean isNearZero(double value) {
        return value > -EPSILON && value < EPSILON;
    }

    /// Normalizes a finite degree value into the half-open range `[0, 360)`.
    ///
    /// Quadrant-sensitive text placement and polar sweep calculations use the normalized result so
    /// equivalent angles share one canonical branch.
    ///
    /// @param degrees angle in degrees
    /// @return the normalized angle
    public static double mod360(double degrees) {
        double normalizedDegrees = degrees % FULL_CIRCLE_DEGREES;
        if (normalizedDegrees < 0.0)
            normalizedDegrees += FULL_CIRCLE_DEGREES;
        return normalizedDegrees;
    }

    /// Computes the sine of an angle expressed in degrees.
    ///
    /// Any whole-turn-equivalent cardinal direction returns an exact canonical result, which keeps
    /// downstream layout code from mistaking a horizontal or vertical direction for a tiny
    /// non-zero slope after conversion noise.
    ///
    /// @param degrees angle in degrees
    /// @return the sine of `degrees`
    public static double sinDeg(double degrees) {
        double normalizedDegrees = mod360(degrees);
        if (normalizedDegrees == 0.0)
            return 0.0;
        if (normalizedDegrees == RIGHT_ANGLE_DEGREES)
            return 1.0;
        if (normalizedDegrees == THREE_QUARTER_TURN_DEGREES)
            return -1.0;
        return Math.sin(toRadians(normalizedDegrees));
    }

    /// Converts radians to degrees without normalizing the result into a canonical turn range.
    ///
    /// @param radians angle in radians
    /// @return `radians` converted to degrees
    public static double toDegrees(double radians) {
        return radians * DEGREES_PER_RADIAN;
    }

    /// Converts degrees to radians without normalizing full turns.
    ///
    /// @param degrees angle in degrees
    /// @return `degrees` converted to radians
    public static double toRadians(double degrees) {
        return degrees * RADIANS_PER_DEGREE;
    }
}
