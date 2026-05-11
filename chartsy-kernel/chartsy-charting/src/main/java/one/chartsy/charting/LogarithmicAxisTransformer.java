package one.chartsy.charting;

/// Applies a logarithmic mapping to positive axis values.
///
/// [Scale#setLogarithmic(double)] installs this transformer together with
/// [LogarithmicStepsDefinition] so tick generation and label formatting operate in logarithmic
/// space. The default base is `10`, which matches the decade-oriented scale behavior used
/// throughout the charting module.
///
/// Forward transformation clamps non-positive inputs to `0.0` instead of throwing. That lets
/// projector and scale code degrade unsupported values to the logarithmic floor instead of aborting
/// the entire conversion. When [#isRoundingToPowers()] is enabled, [#validateInterval(DataInterval)]
/// also snaps interval bounds outward to exact powers of the base so logarithmic windows line up on
/// clean decade boundaries. Viewport code that needs exact user-selected bounds can disable that
/// rounding explicitly.
public class LogarithmicAxisTransformer extends AxisTransformer {
    private static final double ROUNDING_EPSILON = 1.0E-6;

    private double logBase;
    private double logBaseNaturalLog;
    private boolean roundingToPowers;

    /// Creates a base-10 logarithmic transformer with interval rounding enabled.
    public LogarithmicAxisTransformer() {
        this(10.0);
    }

    /// Creates a logarithmic transformer for `logBase`.
    ///
    /// @param logBase strictly positive logarithm base
    /// @throws IllegalArgumentException if `logBase` is not strictly positive
    public LogarithmicAxisTransformer(double logBase) {
        roundingToPowers = true;
        setLogBase(logBase);
    }

    /// Applies the forward logarithmic mapping to `value`.
    ///
    /// Non-positive inputs are clamped to `0.0`, which acts as the logarithmic lower boundary used
    /// by the current charting code.
    @Override
    public double apply(double value) throws AxisTransformerException {
        if (value <= 0.0) {
            return 0.0;
        }
        return Math.log(value) / logBaseNaturalLog;
    }

    /// Returns the configured logarithm base.
    public final double getLogBase() {
        return logBase;
    }

    /// Applies the inverse logarithmic mapping to `value`.
    @Override
    public double inverse(double value) throws AxisTransformerException {
        return Math.exp(value * logBaseNaturalLog);
    }

    /// Returns whether validated intervals are rounded outward to exact powers of the base.
    public boolean isRoundingToPowers() {
        return roundingToPowers;
    }

    /// Replaces the logarithm base.
    ///
    /// @param logBase strictly positive logarithm base
    /// @throws IllegalArgumentException if `logBase` is not strictly positive
    public void setLogBase(double logBase) {
        if (logBase <= 0.0) {
            throw new IllegalArgumentException("Logarithmic base must be strictly positive");
        }
        if (logBase != this.logBase) {
            double oldValue = this.logBase;
            this.logBase = logBase;
            logBaseNaturalLog = Math.log(logBase);
            firePropertyChange("logBase", oldValue, logBase);
        }
    }

    /// Enables or disables outward snapping of validated intervals to exact powers of the base.
    ///
    /// @param roundingToPowers `true` to round interval bounds to logarithmic powers
    public void setRoundingToPowers(boolean roundingToPowers) {
        boolean oldValue = this.roundingToPowers;
        if (roundingToPowers != oldValue) {
            this.roundingToPowers = roundingToPowers;
            firePropertyChange("roundingToPowers", oldValue, roundingToPowers);
        }
    }

    /// Clamps or expands `interval` so it fits this transformer's logarithmic domain.
    ///
    /// When the base is greater than `1.0`, non-positive bounds are clamped to `0.0`. If
    /// [#isRoundingToPowers()] is enabled, the lower bound is rounded down to the nearest power of
    /// the base and the upper bound is rounded up to the nearest power of the base.
    @Override
    public boolean validateInterval(DataInterval interval) {
        boolean changed = false;
        if (getLogBase() > 1.0) {
            double originalMin = interval.min;
            interval.min = roundingToPowers
                    ? roundDownToPower(originalMin)
                    : clampToNonNegative(originalMin);
            changed = originalMin != interval.min;

            double originalMax = interval.max;
            interval.max = roundingToPowers
                    ? roundUpToPower(originalMax)
                    : clampToNonNegative(originalMax);
            changed = changed || originalMax != interval.max;
        }
        return changed;
    }

    private double clampToNonNegative(double value) {
        return (value <= 0.0) ? 0.0 : value;
    }

    private double floorLogExponent(double value) {
        double epsilon = (value > Double.MAX_VALUE - ROUNDING_EPSILON) ? 0.0 : ROUNDING_EPSILON;
        return Math.floor(Math.log(value) / logBaseNaturalLog + epsilon);
    }

    private double roundDownToPower(double value) {
        if (value <= 0.0) {
            return 0.0;
        }
        return Math.pow(getLogBase(), floorLogExponent(value));
    }

    private double roundUpToPower(double value) {
        if (value <= 0.0) {
            return 0.0;
        }

        double exponent = floorLogExponent(value);
        if (Math.pow(getLogBase(), exponent) != value) {
            exponent += 1.0;
        }
        return Math.pow(getLogBase(), exponent);
    }
}
