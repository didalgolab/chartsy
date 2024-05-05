/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.wavelets;

/**
 * Basic class for one wavelet keeping coefficients of the wavelet function, the
 * scaling function, the base wavelength, the forward transform method, and the
 * reverse transform method.
 * 
 * @author Christian Scheiblich (cscheiblich@gmail.com)
 */
public abstract class Wavelet {
    
    /**
     * The coefficients of the mother scaling (low pass filter) for decomposition.
     */
    private double[] primalLowpassCoefficients;
    
    /**
     * The coefficients of the mother wavelet (high pass filter) for
     * decomposition.
     */
    private double[] primalHighpassCoefficients;
    
    /**
     * The coefficients of the mother scaling (low pass filter) for
     * reconstruction.
     */
    private double[] dualLowpassCoefficients;
    
    /**
     * The coefficients of the mother wavelet (high pass filter) for
     * reconstruction.
     */
    private double[] dualHighpassCoefficients;
    
    
    /**
     * Constructor; predefine members to default values or null!
     * 
     * @author Christian Scheiblich (cscheiblich@gmail.com)
     */
    protected Wavelet() {
    }
    
    protected final void setFilterCoefficients(double[] lowpass) {
        // building wavelet as orthogonal (orthonormal) space from
        // scaling coefficients (low pass filter). Have a look into
        // Alfred Haar's wavelet or the Daubechies Wavelet with 2
        // vanishing moments for understanding what is done here. ;-)
        int tapLength = lowpass.length;
        primalLowpassCoefficients = lowpass;
        primalHighpassCoefficients = new double[tapLength];
        for (int i = 0, sign = -1; i < tapLength; i++)
            primalHighpassCoefficients[i] = (sign = -sign)*primalLowpassCoefficients[tapLength-i-1];
        
        // Copy to reconstruction filters due to orthogonality
        dualLowpassCoefficients = lowpass.clone();
        dualHighpassCoefficients = primalHighpassCoefficients.clone();
    }
    
    /**
     * Configures the wavelet filter coefficients.
     * <p>
     * The method assigns the orthonormal coefficients
     * of the scaling (low pass) and wavelet (high pass) filters using the provided values
     * and calculates matching dual scaling and wavelet filters coefficients.
     * <p>
     * The method assumes biorthogonal wavelet construction.
     * The method should be called in a constructor of a custom biorthogonal wavelet implementation.
     * 
     * @param lowpass the primal lowpass filter coefficients
     * @param lpOrigin the index of the first coefficient
     * @param highpass the primal highpass filter coefficients
     * @param hpOrigin
     */
    protected final void setFilterCoefficients(double[] lowpass, int lpOrigin, double[] highpass, int hpOrigin) {
        int dlpOrigin = (2 - hpOrigin - highpass.length);
        int dhpOrigin = (2 - lpOrigin - lowpass.length);
        int origin = Math.min(lpOrigin, hpOrigin);
        origin = Math.min(origin, dlpOrigin);
        origin = Math.min(origin, dhpOrigin);
        
        int tapLength = Math.max(lowpass.length + lpOrigin - origin, highpass.length + hpOrigin - origin);
        tapLength = Math.max(tapLength, lowpass.length + dhpOrigin - origin);
        tapLength = Math.max(tapLength, highpass.length + dlpOrigin - origin);
        primalLowpassCoefficients = new double[tapLength];
        primalHighpassCoefficients = new double[tapLength];
        dualLowpassCoefficients = new double[tapLength];
        dualHighpassCoefficients = new double[tapLength];
        System.arraycopy(lowpass, 0, primalLowpassCoefficients, lpOrigin - origin, lowpass.length);
        System.arraycopy(highpass, 0, primalHighpassCoefficients, hpOrigin - origin, highpass.length);
        for (int i = lowpass.length-1, j = dhpOrigin - origin, sign = -calcCoeffSign(lowpass, i, lpOrigin); i >= 0; i--, j++)
            dualHighpassCoefficients[j] = (sign = -sign)*lowpass[i];
        for (int i = highpass.length-1, j = dlpOrigin - origin, sign = calcCoeffSign(highpass, i, hpOrigin); i >= 0; i--, j++)
            dualLowpassCoefficients[j] = (sign = -sign)*highpass[i];
    }
    
    private static int calcCoeffSign(double[] coeffs, int index, int origin) {
        return 1-(index+origin+coeffs.length%2<<1&2);
    }
    
    /**
     * Returns a copy of the scaling (low pass filter) coefficients of
     * decomposition.
     * 
     * @author Christian Scheiblich (cscheiblich@gmail.com)
     * @return array of length of the mother wavelet wavelength keeping the
     *         decomposition low pass filter coefficients
     */
    public double[] getPrimalLowpassCoefficients() {
        return primalLowpassCoefficients.clone();
    }

    /**
     * @return the primalHighpassCoefficients
     */
    public double[] getPrimalHighpassCoefficients() {
        return primalHighpassCoefficients.clone();
    }
    
    /**
     * @return the dualLowpassCoefficients
     */
    public double[] getDualLowpassCoefficients() {
        return dualLowpassCoefficients.clone();
    }
    
    /**
     * @return the dualHighpassCoefficients
     */
    public double[] getDualHighpassCoefficients() {
        return dualHighpassCoefficients.clone();
    }
    
}