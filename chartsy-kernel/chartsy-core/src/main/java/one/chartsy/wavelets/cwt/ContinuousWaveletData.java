package one.chartsy.wavelets.cwt;

import java.util.ArrayList;
import java.util.List;

/**
 * CWT result class
 */
public class ContinuousWaveletData {
    private double _re[];                // real part of transform
    private double _im[];                // imaginary part of transform
    private final int _rows;                   // rows number
    private final Wavelet _wavelet;            // mother wavelet used for transform
    
    private final int dataDimension;
    private final int octaves;
    private final int voices;
    private final double[] scales;
    
    /**
     * Constructor of ContinuousWaveletData object (in most cases used in CWT algorithms,
     * but can be constructed manually).
     *
     *  @param Scales        functor which provides scales sequence;
     *  @param Translations  functor which provides translations sequence;
     *  @param MotherWavelet mother wavelet;
     *  @param Name          object name.
     */
    public ContinuousWaveletData(int dataDimension,
            Wavelet MotherWavelet, int octaves, int voices, double[] scales)
                    throws IllegalArgumentException
    {
        this.dataDimension = dataDimension;
        this.octaves = octaves;
        this.voices = voices;
        this.scales = scales;
        _rows = scales.length;//Scales.steps();
        
        // transform result cannot be empty
        if (_rows <= 0)
            throw new IllegalArgumentException("Invalid dimensions provided!");
        if (dataDimension <= 0)
            throw new IllegalArgumentException("Invalid dataDimension specification: " + dataDimension);
        
        // copy necessary objects
        _wavelet = MotherWavelet.clone();
        
        // allocate storage
        //_re = new double[ _rows * dataDimension ];
        //_im = new double[ _rows * dataDimension ];
    }
    
    private final List<double[]> realCoeffs = new ArrayList<>();
    
    private final List<double[]> imagCoeffs = new ArrayList<>();
    
    public double[] getScales() {
        return scales;
    }
    
    public List<double[]> getRealCoeffs() {
        return realCoeffs;
    }
    
    public List<double[]> getImagCoeffs() {
        return imagCoeffs;
    }
    
    /**
     * Get real value for given row and column.
     */
    public double re(int row, int col) throws ArrayIndexOutOfBoundsException
    {
        if (row < 0 || row >= _rows || col < 0 || col >= dataDimension)
            throw new ArrayIndexOutOfBoundsException("Dimensions out of bounds!");
        
        return _re[row*dataDimension + col];
    }
    
    /**
     * Set real value for given row and column.
     */
    public void re(int row, int col, double v) throws ArrayIndexOutOfBoundsException
    {
        if (row < 0 || row >= _rows || col < 0 || col >= dataDimension)
            throw new ArrayIndexOutOfBoundsException("Dimensions out of bounds!");
        
        _re[row*dataDimension + col] = v;
    }
    
    /**
     * Get imaginary value for given row and column.
     */
    public double im(int row, int col) throws ArrayIndexOutOfBoundsException
    {
        if (row < 0 || row >= _rows || col < 0 || col >= dataDimension)
            throw new ArrayIndexOutOfBoundsException("Dimensions out of bounds!");
        
        return _im[row*dataDimension + col];
    }
    
    /**
     * Set imaginary value for given row and column.
     */
    public void im(int row, int col, double v) throws ArrayIndexOutOfBoundsException
    {
        if (row < 0 || row >= _rows || col < 0 || col >= dataDimension)
            throw new ArrayIndexOutOfBoundsException("Dimensions out of bounds!");
        
        _im[row*dataDimension + col] = v;
    }
    
    /**
     * Get magnitude for given row and column.
     */
    public double mag(int row, int col) throws ArrayIndexOutOfBoundsException
    {
        if (row < 0 || row >= _rows || col < 0 || col >= dataDimension)
            throw new ArrayIndexOutOfBoundsException("Dimensions out of bounds!");
        
        int idx = row*dataDimension + col;
        return Math.sqrt(_re[idx]*_re[idx] + _im[idx]*_im[idx]);
    }
    
    /**
     * Get angle for given row and column.
     */
    public double ang(int row, int col) throws ArrayIndexOutOfBoundsException
    {
        if (row < 0 || row >= _rows || col < 0 || col >= dataDimension)
            throw new ArrayIndexOutOfBoundsException("Dimensions out of bounds!");
        
        int idx = row*dataDimension + col;
        return Math.atan2(_im[idx], _re[idx]);
    }
    
    /**
     * Get rows count
     */
    public int rows()
    {
        return _rows;
    }
    
    /**
     * Get columns count
     */
    @Deprecated
    public int cols()
    {
        return dataDimension;
    }
    
    
    public Wavelet motherWavelet()
    {
        return _wavelet;
    }
    
    /**
     * Get data proxy for direct acces to internal real data
     */
    public double[] reData()
    {
        return _re;
    }
    
    /**
     * Get data proxy for direct acces to internal imaginary data
     */
    public double[] imData()
    {
        return _im;
    }
}
