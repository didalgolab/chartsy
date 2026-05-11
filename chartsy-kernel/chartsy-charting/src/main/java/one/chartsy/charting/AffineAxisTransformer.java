package one.chartsy.charting;

import java.io.Serial;

/// Applies the affine mapping `transformed = source * scaling + constant`.
///
/// This is the simplest mutable [AxisTransformer] shipped with the charting module. It is used
/// for linear rescaling use cases such as percent-variation overlays, and it is the only
/// transformer that default numeric and time step definitions currently treat as label-preserving:
/// they can generate ticks in transformed space and recover source coordinates with
/// [#inverse(double)].
///
/// Projection code also has a fast path for the exact `AffineAxisTransformer` runtime class, which
/// lets it fold the coefficients directly into a single `AffineTransform` instead of applying the
/// scalar transform point by point. A zero scaling factor is therefore rejected up front so the
/// inverse mapping always remains defined.
public class AffineAxisTransformer extends AxisTransformer {
    @Serial
    private static final long serialVersionUID = 7953443785056581736L;
    private double scaling;
    private double constant;

    /// Creates an affine transformer with the supplied coefficients.
    ///
    /// @param scaling  the multiplicative coefficient
    /// @param constant the additive offset applied after scaling
    /// @throws IllegalArgumentException if `scaling` is `0.0`
    public AffineAxisTransformer(double scaling, double constant) {
        setScaling(scaling);
        setConstant(constant);
    }

    /// Applies the affine forward mapping to `value`.
    @Override
    public double apply(double value) throws AxisTransformerException {
        return value * scaling + constant;
    }

    /// Returns the additive offset applied after scaling.
    public final double getConstant() {
        return constant;
    }

    /// Returns the multiplicative coefficient of the affine mapping.
    public final double getScaling() {
        return scaling;
    }

    /// Applies the inverse affine mapping to `value`.
    @Override
    public double inverse(double value) throws AxisTransformerException {
        return (value - constant) / scaling;
    }

    /// Replaces the additive offset and notifies listeners when it changes.
    ///
    /// @param constant the new additive offset
    public void setConstant(double constant) {
        if (constant != this.constant) {
            double oldValue = this.constant;
            this.constant = constant;
            firePropertyChange("constant", oldValue, constant);
        }
    }

    /// Replaces both affine coefficients.
    ///
    /// This convenience method fires the same property changes as calling [#setScaling(double)]
    /// and [#setConstant(double)] in sequence.
    ///
    /// @param scaling  the new multiplicative coefficient
    /// @param constant the new additive offset
    /// @throws IllegalArgumentException if `scaling` is `0.0`
    public void setDefinition(double scaling, double constant) {
        setScaling(scaling);
        setConstant(constant);
    }

    /// Replaces the multiplicative coefficient and notifies listeners when it changes.
    ///
    /// Negative values are allowed and reverse the transformed axis direction.
    ///
    /// @param scaling the new multiplicative coefficient
    /// @throws IllegalArgumentException if `scaling` is `0.0`
    public void setScaling(double scaling) {
        if (scaling == 0.0)
            throw new IllegalArgumentException("Scaling must not equal 0");

        if (scaling != this.scaling) {
            double oldValue = this.scaling;
            this.scaling = scaling;
            firePropertyChange("scaling", oldValue, scaling);
        }
    }
}
