package one.chartsy.wavelets.cwt;

public class MorletWavelet {
    
    /**
     * Constructs a gaussian wavelet of the specified order.
     * 
     */
    public MorletWavelet() {
    }
    
    
    public boolean isOrthogonal() {
        return false;
    }
    
    public boolean isBiorthogonal() {
        return false;
    }
    
    public double getFourierFactor() {
        return 0.0;//TODO: 2.0 * Math.PI * Math.sqrt(0.4);
    }
}
