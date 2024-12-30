/* Copyright 2018 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.wavelets;

import one.chartsy.data.RealVector;

/**
 * Base class for performing forward and reverse inverse discrete wavelet
 * transforms.
 * <p>
 * Currently the class supports one-dimensional transforms only.
 * 
 * 
 * @author Mariusz Bernacki
 */
public class DiscreteWaveletTransform {
    private static final double SQRT_2 = 1.4142135623730950488;
    private final Wavelet wavelet;
    private final double[] primalLowpass;
    private final double[] primalHighpass;
    private final double[] dualLowpass;
    private final double[] dualHighpass;
    
    
    public DiscreteWaveletTransform(Wavelet wavelet) {
        this.wavelet = wavelet;
        this.primalLowpass = wavelet.getPrimalLowpassCoefficients();
        this.primalHighpass = wavelet.getPrimalHighpassCoefficients();
        this.dualLowpass = wavelet.getDualLowpassCoefficients();
        this.dualHighpass = wavelet.getDualHighpassCoefficients();
    }

    /**
     * Gives the wavelet which this transform is based on.
     *
     * @return the current transform wavelet
     */
    public Wavelet getWavelet() {
        return wavelet;
    }

    /**
     * Performs a 1-D forward transform from time domain to Hilbert domain using
     * one kind of wavelet transform algorithm for a given array of dimension
     * (length) 2^p | pEN; N = 2, 4, 8, 16, 32, 64, 128, ..., and so on.
     */
    public RealVector transform(RealVector data) {
        return transform(data, Integer.MAX_VALUE);
    }
    
    /**
     * Performs a 1-D reverse transform from Hilbert domain to time domain using
     * one kind of wavelet transform algorithm for a given array of dimension
     * (length) 2^p | pEN; N = 2, 4, 8, 16, 32, 64, 128, ..., and so on.
     */
    public RealVector inverseTransform(RealVector data) {
        return inverseTransform(data, Integer.MAX_VALUE);
    }
    
    /**
     * Performs the forward transform for the given array from time domain to
     * Hilbert domain and returns a new array of the same size keeping
     * coefficients of Hilbert domain and should be of length 2 to the power of p
     * -- length = 2^p where p is a positive integer.
     * 
     * @param data
     *          array keeping time domain coefficients
     * @param length
     *          is necessary, due to working only on a part of data not on the
     *          full length of data!
     * @return coefficients represented by frequency domain
     */
    protected double[] stepTransform(double[] data, int length) {
        double[] result = new double[length];
        
        int h = result.length >> 1;
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < primalLowpass.length; j++) {
                int k = 2*i + j;
                while (k >= result.length) {
                    k -= result.length;
                }
                
                result[i] += data[k] * primalLowpass[j];
                result[i + h] += data[k] * primalHighpass[j];
            }
            result[i] *= SQRT_2;
            result[i + h] *= SQRT_2;
        }
        return result;
    }
    
    /**
     * Performs the reverse transform for the given array from Hilbert domain to
     * time domain and returns a new array of the same size keeping coefficients
     * of time domain and should be of length 2 to the power of p -- length = 2^p
     * where p is a positive integer.
     * 
     * @param arrHilb
     *          array keeping frequency domain coefficients
     * @param arrHilbLength
     *          is necessary, due to working only on a part of arrHilb not on the
     *          full length of arrHilb!
     * @return coefficients represented by time domain
     */
    protected double[] stepInverseTransform(double[] arrHilb, int arrHilbLength) {
        double[] result = new double[arrHilbLength];
        int h = result.length >> 1;
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < dualLowpass.length; j++) {
                int k = 2*i + j;
                while (k >= result.length) {
                    k -= result.length;
                }
                
                result[k] += dualLowpass[j] * arrHilb[i] + dualHighpass[j] * arrHilb[i + h];
            }
        }
        for (int i = 0; i < result.length; i++) {
            result[i] *= SQRT_2;
        }
        return result;
    }
    
    /**
     * Performs a 1-D forward transform from time domain to Hilbert domain using
     * one kind of Fast Wavelet Transform (FWT) algorithm for a given array of
     * dimension (length) 2^p | pEN; N = 2, 4, 8, 16, 32, 64, 128, ..., and so on.
     * However, the algorithms stops for a supported level that has to be in the
     * range 0, ..., p of the dimension of the input array; 0 is the time series
     * itself and p is the maximal number of possible levels.
     */
    public RealVector transform(RealVector data, int levels) {
        int n = data.size();
        if ((n & n - 1) != 0)
            throw new UnsupportedOperationException("The array argument length must be a power of 2");
        
        int maxLevel = Integer.numberOfTrailingZeros(n);
        if (levels == Integer.MAX_VALUE)
            levels = maxLevel;
        else if (levels <= 0)
            throw new IllegalArgumentException("Refinement level " + levels + " must be positive");
        else if (levels > maxLevel)
            throw new IllegalArgumentException("Refinement level " + levels + " exceeds maximum possible decomposition level " + maxLevel);
        
        int level = 0;
        int range = n;
        double[] result = data.values();
        result = stepTransform(result, data.size());
        while (++level < levels) {
            double[] subResult = stepTransform(result, range >>= 1);
            System.arraycopy(subResult, 0, result, 0, range);
        }
        return RealVector.from(result);
    }
    
    /**
     * Performs a 1-D reverse transform from Hilbert domain to time domain using
     * one kind of Fast Wavelet Transform (FWT) algorithm for a given array of
     * dimension (length) 2^p | pEN; N = 2, 4, 8, 16, 32, 64, 128, ..., and so on.
     * However, the algorithms starts for at a supported level that has to be in the
     * range 0, ..., p of the dimension of the input array; 0 is the time series
     * itself and p is the maximal number of possible levels. The coefficients of
     * the input array have to match to the supported level.
     */
    public RealVector inverseTransform(RealVector data, int levels) {
        int n = data.size();
        if ((n & n - 1) != 0)
            throw new UnsupportedOperationException("The array argument length must be a power of 2");
        
        int maxLevel = Integer.numberOfTrailingZeros(n);
        if (levels == Integer.MAX_VALUE)
            levels = maxLevel;
        else if (levels <= 0)
            throw new IllegalArgumentException("Refinement level " + levels + " must be positive");
        else if (levels > maxLevel)
            throw new IllegalArgumentException("Refinement level " + levels + " exceeds maximum possible decomposition level " + maxLevel);
        
        double[] result = data.values();
        int h = 2 << (maxLevel - levels);
        while (h <= result.length && h >= 2) {
            double[] subResult = stepInverseTransform(result, h);
            System.arraycopy(subResult, 0, result, 0, h);
            h <<= 1;
        }
        return RealVector.from(result);
    }
}
