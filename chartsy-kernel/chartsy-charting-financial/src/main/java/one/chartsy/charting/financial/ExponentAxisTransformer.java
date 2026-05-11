package one.chartsy.charting.financial;

import one.chartsy.charting.AxisTransformer;
import one.chartsy.charting.AxisTransformerException;
import one.chartsy.charting.DataInterval;

/**
 * An axis transformer that applies a given exponent.
 * <p>
 * For example, the following code creates a transformer that computes the
 * square root of values along a given axis:
 * 
 * <pre>
 * ExponentAxisTransformer t = new ExponentAxisTransformer(.5);
 * </pre>
 *
 */
public class ExponentAxisTransformer extends AxisTransformer {
  private double exponent;
  private double exponentInv;

  /**
   * Initializes a new <code>ExponentAxisTransformer</code> with the specified
   * exponent.
   * 
   * @exception IllegalArgumentException
   *              If the exponent is equal to zero.
   */
  public ExponentAxisTransformer(double exponent) {
    setExponent(exponent);
  }

  /**
   * Sets the exponent.
   * 
   * @exception IllegalArgumentException
   *              If the exponent is equal to zero.
   */
  public void setExponent(double exponent) {
    if (exponent == 0)
      throw new IllegalArgumentException("Exponent cannot be zero");
    if (exponent == this.exponent)
      return;

    double oldExponent = this.exponent;
    this.exponent = exponent;
    exponentInv = 1 / exponent;
    firePropertyChange("exponent", oldExponent, this.exponent);
  }

  /** Applies the transformation to the specified value. */
  @Override
  public double apply(double value) throws AxisTransformerException {
    return Math.pow(value, exponent);
  }

  /** Applies the inverse transformation to the specified value. */
  @Override
  public double inverse(double value) throws AxisTransformerException {
    return Math.pow(value, exponentInv);
  }

  /**
   * Modifies the specified interval so that it is consistent with the
   * definition domain of the transformation. This method truncates the
   * specified interval so that it only contains positive values.
   */
  @Override
  public boolean validateInterval(DataInterval itv) {
    boolean modified = false;
    if (itv.getMin() < 0) {
      itv.setMin(0);
      modified = true;
    }
    if (itv.getMax() < 0) {
      itv.setMax(0);
      modified = true;
    }
    return modified;
  }
}

