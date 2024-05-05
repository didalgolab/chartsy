/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.wavelets.cwt;

import java.lang.ref.SoftReference;
import java.util.BitSet;

public class Fourier {
    /** The current object fourier transform length. */
    private final int length;
    /** The fourier transform method to be used. */
    private final Method method;
    
    
    public Fourier(int length) {
        this.length = length;
        if (length <= 0)
            throw new IllegalArgumentException("Length parameter " + length + " must be a positive integer");
        if ((length & (length - 1)) == 0) // Is power of 2
            method = new Radix2DIT(length);
        else  // More complicated algorithm for arbitrary sizes
            method = new BluesteinFFT(length);
    }
    
    public interface Method {
        
        void fft(double[] re, double[] im);
        
    }
    
    /**
     * Computes the discrete Fourier transform (DFT) of the given complex vector, storing the result back into the vector.
     * The vector can have any length.
     * <p>
     * The method uses {@code FourierParameters} = {1,-1}
     * 
     * @param re
     * @param im
     */
    public static void transform(double[] re, double[] im) {
        if (re.length != im.length)
            throw new IllegalArgumentException("Mismatched lengths");
        
        new Fourier(re.length).method.fft(re, im);
    }
    
    /* 
     * Computes the inverse discrete Fourier transform (IDFT) of the given complex vector, storing the result back into the vector.
     * The vector can have any length. This is a wrapper function. This transform does not perform scaling, so the inverse is not a true inverse.
     */
    /**
     * The method uses FourierParameters = {1,1}
     * 
     * @param real
     * @param imag
     */
    public static void inverseTransform(double[] re, double[] im) {
        new Fourier(re.length).method.fft(im, re);
    }
    
    private static void swap(double[] arr, int i, int j) {
        double tmp = arr[i];
        arr[i] = arr[j];
        arr[j] = tmp;
    }
    
    // Swaps the row
    private static void swap(double[] arr, int i, int j, int count) {
        for (int k = 0; k < count; k++)
            swap(arr, i++, j++);
    }
    
    public static class Radix2DIT implements Method {
        private final int length;
        private final double[] cosTable, sinTable;
        
        
        public Radix2DIT(int length) {
            this.length = length;
            cosTable = new double[length / 2];
            sinTable = new double[length / 2];
            double angle = 2 * Math.PI / length;
            for (int i = 0; i < length / 2; i++) {
                cosTable[i] = Math.cos(angle * i);
                sinTable[i] = Math.sin(angle * i);
            }
        }
        
        /**
         * Computes the discrete Fourier transform (DFT) of the given complex
         * vector, storing the result back into the vector. The vector's length
         * must be a power of 2. Uses the Cooley-Tukey decimation-in-time
         * radix-2 algorithm.
         */
        @Override
        public void fft(double[] re, double[] im) {
            // Initialization
            if (re.length != im.length)
                throw new IllegalArgumentException("Mismatched lengths");
            int n = re.length;
            int levels = 31 - Integer.numberOfLeadingZeros(n);  // Equal to floor(log2(n))
            if (1 << levels != n)
                throw new IllegalArgumentException("Length is not a power of 2");
            
            // Bit-reversed addressing permutation
            for (int i = 0; i < n; i++) {
                int j = Integer.reverse(i) >>> (32 - levels);
            if (j > i) {
                swap(re, i, j);
                swap(im, i, j);
            }
            }
            
            // Cooley-Tukey decimation-in-time radix-2 FFT
            for (int size = 2; size <= n; size *= 2) {
                int halfsize = size / 2;
                int tablestep = n / size;
                for (int i = 0; i < n; i += size) {
                    for (int j = i, k = 0; j < i + halfsize; j++, k += tablestep) {
                        double tpre =  re[j+halfsize] * cosTable[k] + im[j+halfsize] * sinTable[k];
                        double tpim = -re[j+halfsize] * sinTable[k] + im[j+halfsize] * cosTable[k];
                        re[j + halfsize] = re[j] - tpre;
                        im[j + halfsize] = im[j] - tpim;
                        re[j] += tpre;
                        im[j] += tpim;
                    }
                }
                if (size == n)  // Prevent overflow in 'size *= 2'
                    break;
            }
        }
    }
    
    static class BluesteinFFT implements Method {
        /** Transform length supported by this method. */
        private final int length;
        /** The trigonometric tables of the transform. */
        private final double[] cosTable, sinTable;
        
        BluesteinFFT(int length) {
            if (length >= 0x20000000)
                throw new IllegalArgumentException("Transform length too long for a Bluestein method");
            
            // Build trigonometric tables for the transform
            this.length = length;
            cosTable = new double[length];
            sinTable = new double[length];
            double angle = Math.PI / length;
            for (int i = 0; i < length; i++) {
                long j = (long)i * i % (length * 2);
                cosTable[i] = Math.cos(angle * j);
                sinTable[i] = Math.sin(angle * j);
            }
        }
        
        /**
         * Computes the discrete Fourier transform (DFT) of the given complex
         * vector, storing the result back into the vector. The vector can have
         * any length. This requires the convolution function, which in turn
         * requires the radix-2 FFT function. Uses Bluestein's chirp z-transform
         * algorithm.
         */
        @Override
        public void fft(double[] re, double[] im) {
            // Find a power-of-2 convolution length m such that m >= n * 2 + 1
            if (re.length != im.length)
                throw new IllegalArgumentException("Mismatched lengths");
            int n = re.length;
            if (n != this.length)
                throw new IllegalArgumentException("Expected transform size " + this.length);
            int m = Integer.highestOneBit(1 + 2*n) << 1;
            
            // Temporary vectors and preprocessing
            double[] re1 = new double[m];
            double[] im1 = new double[m];
            for (int i = 0; i < n; i++) {
                re1[i] =  re[i] * cosTable[i] + im[i] * sinTable[i];
                im1[i] = -re[i] * sinTable[i] + im[i] * cosTable[i];
            }
            double[] re2 = new double[m];
            double[] im2 = new double[m];
            re2[0] = cosTable[0];
            im2[0] = sinTable[0];
            for (int i = 1; i < n; i++) {
                re2[i] = re2[m - i] = cosTable[i];
                im2[i] = im2[m - i] = sinTable[i];
            }
            
            // Convolution
            convolve(re1, im1, re2, im2);
            
            // Postprocessing
            for (int i = 0; i < n; i++) {
                re[i] =  re1[i] * cosTable[i] + im1[i] * sinTable[i];
                im[i] = -re1[i] * sinTable[i] + im1[i] * cosTable[i];
            }
        }
        
        /**
         * Computes the circular convolution of the given complex vectors. Each
         * vector's length must be the same.
         */
        protected static void convolve(double[] re1, double[] im1, double[] re2, double[] im2) {
            if (re1.length != im1.length || re1.length != re2.length || re2.length != im2.length)
                throw new IllegalArgumentException("Mismatched lengths");
            
            int n = re1.length;
            
            Fourier fft = new Fourier(n);
            Fourier.transform(re1, im1);
            Fourier.transform(re2, im2);
            for (int i = 0; i < n; i++) {
                double r0 = re1[i]*re2[i] - im1[i]*im2[i];
                im1[i] = im1[i]*re2[i] + re1[i]*im2[i];
                re1[i] = r0;
            }
            inverseTransform(re1, im1);
            double scale = 1.0/n;
            for (int i = 0; i < n; i++) {  // Scaling (because this FFT implementation omits it)
                re1[i] *= scale;
                im1[i] *= scale;
            }
        }
    }
    
    /**
     * The Cooley-Tukey Radix-4 Decimation-in-Frequency FFT algorithm.<br/>
     * The length of arrays x and y must be a power of 4.
     * 
     */
    public void radix4FFT(double[] X, double[] Y) {
        //REAL X(1), Y(1)
        int N = X.length, M = Integer.numberOfTrailingZeros(N) / 2;
        int N2 = N;
        for (int K = 0; K < M; K++) { //DO 10
            int N1 = N2;
            N2 = N2/4;
            double E = 6.283185307179586/N1;
            // main butterflies
            { // special first case: J=0
                // BUTTERFLIES WITH SAME W---------------
                for (int I = 0; I < N; I += N1) { //DO 30
                    int I1 = I + N2;
                    int I2 = I1 + N2;
                    int I3 = I2 + N2;
                    double R1 = X[I ] + X[I2];
                    double R3 = X[I ] - X[I2];
                    double S1 = Y[I ] + Y[I2];
                    double S3 = Y[I ] - Y[I2];
                    double R2 = X[I1] + X[I3];
                    double R4 = X[I1] - X[I3];
                    double S2 = Y[I1] + Y[I3];
                    double S4 = Y[I1] - Y[I3];
                    X[I] = R1 + R2;
                    Y[I] = S1 + S2;
                    X[I1] = R3 + S4;
                    Y[I1] = S3 - R4;
                    X[I2] = R1 - R2;
                    Y[I2] = S1 - S2;
                    X[I3] = R3 - S4;
                    Y[I3] = S3 + R4;
                }
            }
            for (int J = 1; J < N2; J++) {//DO 20
                int A = J << (K*2);
                int B = A*2;
                int C = A + B;
                double CO1 = X[A];//Math.cos(A);
                double CO2 = X[B];//Math.cos(B);
                //double CO3 = WR[C];//Math.cos(C);
                double SI1 = Y[A];//Math.sin(A);
                double SI2 = Y[B];//Math.sin(B);
                //double SI3 = WI[C];//Math.sin(C);
                double CO3 = CO1*CO2 - SI1*SI2;
                double SI3 = SI1*CO2 + CO1*SI2;
                // BUTTERFLIES WITH SAME W---------------
                for (int I = J; I < N; I += N1) { //DO 30
                    int I1 = I + N2;
                    int I2 = I1 + N2;
                    int I3 = I2 + N2;
                    double R1 = X[I ] + X[I2];
                    double R3 = X[I ] - X[I2];
                    double S1 = Y[I ] + Y[I2];
                    double S3 = Y[I ] - Y[I2];
                    double R2 = X[I1] + X[I3];
                    double R4 = X[I1] - X[I3];
                    double S2 = Y[I1] + Y[I3];
                    double S4 = Y[I1] - Y[I3];
                    X[I] = R1 + R2;
                    R2 = R1 - R2;
                    R1 = R3 - S4;
                    R3 = R3 + S4;
                    Y[I] = S1 + S2;
                    S2 = S1 - S2;
                    S1 = S3 + R4;
                    S3 = S3 - R4;
                    X[I1] = CO1*R3 + SI1*S3;
                    Y[I1] = CO1*S3 - SI1*R3;
                    X[I2] = CO2*R2 + SI2*S2;
                    Y[I2] = CO2*S2 - SI2*R2;
                    X[I3] = CO3*R1 + SI3*S1;
                    Y[I3] = CO3*S1 - SI3*R1;
                }
            }
        }
    }
}