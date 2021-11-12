package one.chartsy.data;

/**
 * An AdjustmentMethod is an option for methods RandomWalk.bootstrap and other
 * similar that specifies what adjustment strategy should be used.
 * 
 * @author Mariusz Bernacki
 *
 */
@FunctionalInterface
public interface AdjustmentMethod {
    
    /**
     * The absolute (price difference based) adjustment.
     */
    AdjustmentMethod ABSOLUTE = (x0, y0, x1) -> x1 + y0 - x0;
    
    /**
     * The relative (logarithmic distance based) adjustment.
     */
    AdjustmentMethod RELATIVE = (x0, y0, x1) -> y0 / x0 * x1;
    
    /**
     * Applies the scaling to the given coordinate point. Extrapolates a {@code y1}
     * based on the specified {@code x1} start point and a given sample vector
     * {@code (x0, y0)}
     * 
     * @param x0
     *            the sample vector start point
     * @param y0
     *            the sample vector end point
     * @param x1
     *            the target vector start point
     * @return the target vector end point
     */
    double calculate(double x0, double y0, double x1);

}
