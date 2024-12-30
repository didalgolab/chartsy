package one.chartsy.wavelets.cwt;


/**
 * The Morlet wavelet
 */
public class Gabbor extends Wavelet {
    /**
     * Default wavelet name
     */
    public final static String NAME = "Gabor";
    /**
     * Default central frequency value
     */
    //    public final static double FC = 1.0 / Math.PI;
    /**
     * Default L2 norm. c = 1 / Pi^(1/4)
     */
    public final static double C = 0.75112554446494248286;
    /**
     * Default radius
     */
    //    public final static double R = 5.0;
    
    private final double frequency;
    
    public Gabbor()
    {
        this(6);
    }
    /**
     * Construct Mexican Hat wavelet
     */
    public Gabbor(double frequency)
    {
        super(NAME);
        this.frequency = frequency;
    }
    
    @Override
    public double reT(double t)
    {
        return C * Math.cos(frequency * t) * Math.exp(-t * t * 0.5);
    }
    
    @Override
    public double imT(double t)
    {
        return C * Math.sin(frequency * t) * Math.exp(-t * t * 0.5);
    }
    
    @Override
    public double reF(double w)
    {
        if (w <= 0.0)
            return 0.0;
        return C * Math.sqrt(2.0 * Math.PI)
                * Math.exp(-0.5 * (w - frequency) * (w - frequency));
    }
    
    @Override
    public double imF(double w)
    {
        return 0.0;
    }
}
