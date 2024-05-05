package one.chartsy.wavelets.cwt;

public class MexicanHatWavelet {
    
    /** The order of derivative of the wavelet. */
    private final double width;
    
    /**
     * Constructs a gaussian wavelet of the specified order.
     * 
     * @param frequency
     *            the frequency of the wavelet
     * @throws IllegalArgumentException
     *             if the {@code frequency} is not positive
     */
    public MexicanHatWavelet(double width) {
        if (width <= 0.0)
            throw new IllegalArgumentException("Specified width argument " + width + " should be a positive number.");
        
        this.width = width;
    }
    
    
    public boolean isOrthogonal() {
        return false;
    }
    
    public boolean isBiorthogonal() {
        return false;
    }
    
    public double getFourierFactor() {
        return 2.0 * width * Math.PI * Math.sqrt(0.4);
    }
}
