package one.chartsy.wavelets.cwt;


/**
 * Complex Morlet wavelet
 */
public class ComplexMorlet extends Wavelet {
    /**
     * Default wavelet name
     */
    public final static String NAME = "ComplexMorlet";
    /**
     * Default central frequency value
     */
    public final static double FC = 0.8;
    /**
     * Default bandwidth parameter
     */
    public final static double FB = 2.0;
    
    private final double _fc;    // central frequency
    private final double _fb;    // bandwidth parameter
    private final double _c;     // L2 norm
    // effective support params
    private final double _effl;
    private final double _effr;
    
    /**
     * Construct Complex Morlet wavelet with default
     * parameters.
     */
    public ComplexMorlet()
    {
        super(NAME);
        
        _fc = FC;
        _fb = FB;
        // compute L2 norm ...
        _c = 1.0 / Math.sqrt(Math.PI * _fb);
        // ... and effective support boundary values
        _effl = -2.0*_fb;
        _effr = +2.0*_fb;
    }
    
    /**
     * Construct Complex Morlet wavelet with user-provided
     * parameters.
     *
     *  @param Fc central frequency;
     *  @param Fb bandwidth parameter.
     */
    public ComplexMorlet(double Fc, double Fb)
    {
        super(NAME);
        if (Fc <= 0.0 || Fb <= 0.0)
            throw new IllegalArgumentException("Invalid parameter passed!");
        
        _fc = Fc;
        _fb = Fb;
        _c = 1.0 / Math.sqrt(Math.PI * _fb);
        _effl = -2.0*_fb;
        _effr = +2.0*_fb;
    }
    
    public static void main(String[] args) {
        System.out.println(new ComplexMorlet().reT(0.0));
    }
    
    @Override
    public double reT(double t)
    {
        return _c * Math.exp(-(t*t) / _fb) * Math.cos(2.0 * Math.PI * _fc * t);
    }
    
    @Override
    public double imT(double t)
    {
        return _c * Math.exp(-(t*t) / _fb) * Math.sin(2.0 * Math.PI * _fc * t);
    }
    
    @Override
    public double reF(double w)
    {
        double br;
        
        br = (w - 2.0 * Math.PI * _fc);
        
        return Math.exp(-_fb * br * br / 4.0);
    }
    
    @Override
    public double imF(double w)
    {
        return 0.0;
    }
    
    /**
     * Returns bandwidth parameter
     */
    public double fBand()
    {
        return _fb;
    }
    
    public double cFreq()
    {
        return _fc;
    }
    
    public double effL()
    {
        return _effl;
    }
    
    public double effR()
    {
        return _effr;
    }
}
