package one.chartsy.wavelets.cwt;


/**
 * Mexican Hat wavelet
 */
public class MexicanHat extends Wavelet {
    /**
     * Default wavelet name
     */
    public final static String NAME = "MexicanHat";
    /**
     * Default central frequency value
     */
    public final static double FC = 1.0 / Math.PI;
    /**
     * Default L2 norm. c = 2 / ( sqrt(3) * pi^(1/4) )
     */
    public final static double C = 0.86732507058407751832;
    /**
     * Default radius
     */
    public final static double R = 5.0;
    
    /**
     * Construct Mexican Hat wavelet
     */
    public MexicanHat()
    {
        super(NAME);
    }
    
    @Override
    public double getFourierFactor() {
        return 2.0 * Math.PI * Math.sqrt(0.4);
    }
    
    
    @Override
    public double reT(double t)
    {
        double t2 = t * t;
        return C * (1.0 - t2) * Math.exp(-t2 / 2.0);
    }
    
    @Override
    public double imT(double t)
    {
        return 0.0;
    }
    
    @Override
    public double reF(double w)
    {
        w = w * w;
        return C * Math.sqrt(2.0 * Math.PI) * w * Math.exp(-w / 2.0);
    }
    
    @Override
    public double imF(double w)
    {
        return 0.0;
    }
    
    public double cFreq()
    {
        return FC;
    }
    
    public double effL()
    {
        return -R;
    }
    
    public double effR()
    {
        return +R;
    }
}
