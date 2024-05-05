package one.chartsy.wavelets.cwt;


/**
 * Abstract wavelet class
 */
public abstract class Wavelet implements Cloneable {
    private final String _name;  // Wavelet name
    
    /**
     * Wavelet constructor
     *
     *  @param Name wavelet name.
     */
    protected Wavelet(String Name)
    {
        _name = Name;
    }
    
    public double getFourierFactor() {
        throw new UnsupportedOperationException("getFourierFactor");
    }
    
    
    
    /**
     * Real part of a wavelet in Time Domain
     */
    public abstract double reT(double t);
    /**
     * Imaginary part of a wavelet in Time Domain
     */
    public abstract double imT(double t);
    /**
     * Real part of a wavelet in Frequency Domain
     */
    public abstract double reF(double t);
    /**
     * Imaginary part of a wavelet in Frequency Domain
     */
    public abstract double imF(double t);
    
    /**
     * Wavelet name
     */
    public String name()
    {
        return _name;
    }
    
    /**
     * Used to obtain object clone
     */
    @Override
    public Wavelet clone()
    {
        try {
            return (Wavelet)super.clone();
        }
        catch (CloneNotSupportedException ex) {
            // Cloneable implemented. That is not possible!
            throw new Error("Unknown Error!");
        }
    }
}
