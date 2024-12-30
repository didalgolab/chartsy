package one.chartsy.wavelets.cwt;

public class GaborWavelet {
    
    /** The order of derivative of the wavelet. */
    private final double frequency;
    
    /**
     * Constructs a gaussian wavelet of the specified order.
     * 
     * @param frequency
     *            the frequency of the wavelet
     * @throws IllegalArgumentException
     *             if the {@code frequency} is not positive
     */
    public GaborWavelet(double frequency) {
        if (frequency <= 0.0)
            throw new IllegalArgumentException("Nondimensional frequency argument " + frequency + " should be a positive number.");
        
        this.frequency = frequency;
    }
    
    
    public boolean isOrthogonal() {
        return false;
    }
    
    public boolean isBiorthogonal() {
        return false;
    }
    
    public double getFourierFactor() {
        return 4.0 * Math.PI / (frequency + Math.sqrt(2.0 + frequency*frequency));
    }
}
