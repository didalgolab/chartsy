/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.wavelets;

/**
 * Represents a Haar wavelet.
 * <p>
 * The {@code HaarWavelet} defines a family of ortonormal wavelets.<br>
 * It is the simplest possible wavelet introduced in 1909 by Alfred Haar.<br>
 * The scaling and wavelet functions are "square-shaped", have compact support
 * lengths of 1, have 1 vanishing moment and are symmetric. The wavelet is also
 * known as {@code DB1}. The technical disadvantage of the Haar wavelet is that
 * it is not continuous, and therefore not differentiable. This property can,
 * however, be an advantage for the analysis of signals with sudden transitions.
 * 
 * 
 * @author Mariusz Bernacki
 * @see Wikipedia, the free encyclopedia: <a href="https://en.wikipedia.org/wiki/Haar_wavelet" target="_top">Haar wavelet</a>
 */
public class HaarWavelet extends Wavelet {
    
    /**
     * Constructs a Haar wavelet instance.
     * 
     */
    public HaarWavelet() {
        setFilterCoefficients(new double[] { 0.5, 0.5 });
    }
}
