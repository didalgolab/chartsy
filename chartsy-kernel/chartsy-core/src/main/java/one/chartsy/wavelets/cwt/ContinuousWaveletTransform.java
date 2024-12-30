package one.chartsy.wavelets.cwt;


/**
 * Collection of CWT algorithms (and CWT helpers)
 */
public final class ContinuousWaveletTransform {
    
    private final Wavelet wavelet;
    
    private final double sampleRate = 1.;
    
    private final int voices = 4;
    
    public ContinuousWaveletTransform(Wavelet wavelet) {
        this.wavelet = wavelet;
    }
    
    protected double[] createScales(int noct, int nvoc) {
        double[] scales = new double[noct * nvoc];
        int k = 0;
        for (int oct = 0; oct < noct; oct++)
            for (int voc = 0; voc < nvoc; voc++)
                scales[k++] = createScale(oct, voc, nvoc);
        return scales;
    }
    
    protected double createScale(int oct, int voc, int nvoc) {
        double scale = 1.0 / wavelet.getFourierFactor();
        return scale * Math.pow(2.0, oct + (voc + 1)/(double)nvoc);
    }
    
    /**
     * CWT computation using FFT (fast Fourier transform).
     *
     *  @param s             source signal need to be transformed;
     *  @param Scales        functor which provides scales sequence for
     *                       transform;
     *  @param MotherWavelet mother wavelet used in computations;
     *  @param Name          name which will be assigned to result object.
     *
     *  @return Returns ContinuousWaveletData obect as a result.
     */
    public ContinuousWaveletData transform(double[] f_re, double[] f_im)
    {
        if (f_re.length != f_im.length)
            throw new IllegalArgumentException("Array size mismatch");
        
        // Result
        ContinuousWaveletData wt;
        // indexes and dimensions
        int rows, cols;
        // signal params
        int n = f_re.length;
        // references to internal data
        double wt_re[], wt_im[];
        // variables for wavelet computation
        double w, W_re, W_im;
        // precomputed values
        int dy_cols, dy_cols_dx;
        double sqrt_a_n;
        double twoPIn = 2.0 * Math.PI / (double)n;
        
        
        // create result object
        int octaves = Integer.numberOfTrailingZeros(Integer.highestOneBit(n/2));
        wt = new ContinuousWaveletData(f_re.length, wavelet, octaves, voices, createScales(octaves, voices));
        
        // obtain result dimensions and data references
        rows = wt.rows();
        cols = wt.cols();
        
        // forward Fourier transform of a signal copy
        Fourier.inverseTransform(f_re, f_im);
        
        // Scales
        for (double a : wt.getScales())
        {
            // get current scale
            //a = Scales.evaluate(dy) * sampleRate;
            a *= sampleRate;
            if (a == 0.0) a = Double.MIN_VALUE;
            
            sqrt_a_n = Math.sqrt(a) / (double)n; // precompution
            
            // precomputed starting index of result row
            dy_cols = 0;//dy * cols;
            wt_re = new double[n];
            wt_im = new double[n];
            
            // Convolute
            for (int dx = 0; dx < cols; dx++)
            {
                // calculate wave number w
                if (dx<=n>>1)
                    w = +twoPIn * a * (double)(dx);
                else
                    w = -twoPIn * a * (double)(n-dx);
                
                // calculate wavelet
                W_re = sqrt_a_n * wavelet.reF(w);
                W_im = sqrt_a_n * wavelet.imF(w);
                
                // index of result point
                dy_cols_dx = dy_cols + dx;
                
                // compute result
                wt_re[dy_cols_dx] = f_re[dx] * W_re + f_im[dx] * W_im;
                wt_im[dy_cols_dx] = f_im[dx] * W_re - f_re[dx] * W_im;
            }
            // inverse Fourier transform
            Fourier.transform(wt_re, wt_im);
            wt.getRealCoeffs().add(wt_re);
            wt.getImagCoeffs().add(wt_im);
        }
        
        return wt;
    }
}
