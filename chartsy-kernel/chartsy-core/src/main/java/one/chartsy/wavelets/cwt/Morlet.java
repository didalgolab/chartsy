package one.chartsy.wavelets.cwt;


/**
 * The Morlet wavelet
 * 
 * Scales:
	List[Rule[List[1,1],1.0274521872084545`],Rule[List[1,2],1.221853451353402`],Rule[List[1,3],1.4530368178400965`],Rule[List[1,4],1.7279617221363557`]]
 */
public class Morlet extends Wavelet {
    /**
     * Default wavelet name
     */
    public final static String NAME = "Morlet";
    /**
     * Default central frequency value
     */
    //    public final static double FC = 1.0 / Math.PI;
    /**
     * Default L2 norm. c = 1 / Pi^4
     */
    public final static double C = 0.75112554446494248286;
    /**
     * Default radius
     */
    //    public final static double R = 5.0;
    
    /**
     * Construct Mexican Hat wavelet
     */
    public Morlet()
    {
        super(NAME);
    }
    
    @Override
    public double reT(double t)
    {
        double t2 = t * t;
        return C * Math.cos(5.3364462566369963273 * t) * Math.exp(-t2 / 2.0);
    }
    
    @Override
    public double imT(double t)
    {
        return 0.0;
    }
    
    @Override
    public double reF(double w)
    {
        if (w <= 0)
            return 0.0;
        double w2 = w * w;
        return Math.sqrt(2.0)
                * Math.sqrt(Math.sqrt(Math.PI))
                * Math.exp(-(2.0 * Math.PI * Math.PI + w2 * Math.log(2.0)) / Math.log(4))
                * Math.cosh(2.0 * Math.PI * w / Math.sqrt(Math.log(4.0)));
    }
    
    @Override
    public double imF(double w)
    {
        return 0.0;
    }
}
