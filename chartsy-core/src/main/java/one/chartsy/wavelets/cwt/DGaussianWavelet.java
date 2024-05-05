/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.wavelets.cwt;

public class DGaussianWavelet {
    
    /** The order of derivative of the wavelet. */
    private final int order;
    
    /**
     * Constructs a gaussian wavelet of the specified order.
     * 
     * @param order
     *            the derivative order of the wavelet
     * @throws IllegalArgumentException
     *             if the {@code order} is not positive
     */
    public DGaussianWavelet(int order) {
        if (order <= 0)
            throw new IllegalArgumentException("Specified order of derivative " + order + " should be a positive integer.");
        
        this.order = order;
    }
    
    
    public boolean isOrthogonal() {
        return false;
    }
    
    public boolean isBiorthogonal() {
        return false;
    }
    
    public double getFourierFactor() {
        return 2.0 * Math.PI * Math.sqrt(2.0 / (order + 1));
    }
}
