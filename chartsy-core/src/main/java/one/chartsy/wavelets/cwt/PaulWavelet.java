package one.chartsy.wavelets.cwt;

public class PaulWavelet {
    
    /** The order of derivative of the wavelet. */
    private final int order;
    
    /**
     * Constructs a gaussian wavelet of the specified order.
     * 
     * @param order
     *            the derivative order of the wavelet
     * @throws IllegalArgumentException
     *             if the {@code order} is not positive
     */
    public PaulWavelet(int order) {
        if (order <= 0)
            throw new IllegalArgumentException("Specified order " + order + " should be a positive integer.");
        
        this.order = order;
    }
    
    
    public boolean isOrthogonal() {
        return false;
    }
    
    public boolean isBiorthogonal() {
        return false;
    }
    
    public double getFourierFactor() {
        return 4.0 * Math.PI / (2*order + 1);
    }
}
