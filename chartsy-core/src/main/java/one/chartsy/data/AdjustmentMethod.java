package one.chartsy.data;

/**
 * Standard implementations of an {@code AdjustmentFunction}.
 */
public enum AdjustmentMethod implements AdjustmentFunction {
    
    /** The absolute (price difference based) adjustment. */
    ABSOLUTE,
    
    /** The relative (logarithmic distance based) adjustment. */
    RELATIVE;

    @Override
    public double calculate(double x0, double y0, double x1) {
        return switch (this) {
            case ABSOLUTE -> y0 - x0 + x1;
            case RELATIVE -> y0 / x0 * x1;
        };
    }
}
