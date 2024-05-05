package one.chartsy.wavelets;

import one.chartsy.wavelets.LaurentPolynomial.OfDouble;

/**
 * Represents a family of biorthogonal wavelets.
 * <p>
 * The wavelet coefficients are constructed analytically using a recipe given by
 * Godavarthy in [1].
 * 
 * @author Mariusz Bernacki
 * @see [1] S. Godavarthy. �Generating Spline Wavelets.� Proceedings of the 36th
 *      Annual Southeast Regional Conference, 1998.
 */
public class BiorthogonalSplineWavelet extends Wavelet {
    
    /**
     * Constructs a biorthogonal spline wavelet of order {@code 4} and dual
     * order {@code 2}.
     * 
     */
    public BiorthogonalSplineWavelet() {
        this(4, 2);
    }
    
    /**
     * Constructs a biorthogonal spline wavelet of given primal and dual order.
     * <p>
     * The specified parameters {@code n} and {@code m} must be both odd or both
     * even. The specified parameters cannot be negative or zero.
     * 
     * @param n
     *            the order of the wavelet
     * @param m
     *            the dual order of the wavelet
     * @throws IllegalArgumentException
     *             if {@code n} and {@code m} are not both odd or even positive
     *             integers
     */
    public BiorthogonalSplineWavelet(int n, int m) {
        if (n <= 0)
            throw new IllegalArgumentException("The first BiorthogonalSplineWavelet parameter " + n + " must be a positive integer");
        if (m <= 0)
            throw new IllegalArgumentException("The second BiorthogonalSplineWavelet parameter " + m + " must be a positive integer");
        if ((n & 1) != (m & 1))
            throw new IllegalArgumentException("BiorthogonalSplineWavelet parameters (" + n + ", " + m + ") must be both odd or both even");
        
        setFilterCoefficients(
                calcLowpassCoeffs(n, m), 1-m-n/2,
                calcHighpassCoeffs(n), -(n-1)/2);
    }
    
    /**
     * Calculates analytically the highpass biorthogonal spline wavelet
     * coefficients.
     * 
     * @param n
     *            the wavelet order
     * @return the primal highpass wavelet filter coefficients
     */
    private static double[] calcHighpassCoeffs(int n) {
        double[] coeffs = new double[n + 1];
        double scale = 1.0/(1 << n);
        long coeff = 1;
        int sign = 1 - (n & 2);
        for (int i = 0; i <= n; i++) {
            if (i > 0)
                coeff = coeff*(n - i + 1)/i; // Binomial(n,i)
            coeffs[i] = coeff*scale*(sign = -sign);
        }
        return coeffs;
    }
    
    /**
     * Calculates analytically the lowpass biorthogonal spline wavelet
     * coefficients.
     * <p>
     * The coefficients are obtained from the expansion of a special Laurent
     * polynomial as shown by Godavarthy in [1].<br>
     * 
     * @param n
     *            the wavelet order
     * @return the primal highpass wavelet filter coefficients
     * @see [1] S. Godavarthy. �Generating Spline Wavelets.� Proceedings of the
     *      36th Annual Southeast Regional Conference, 1998.
     */
    private static double[] calcLowpassCoeffs(int n, int m) {
        final int mn = -1 + (m + n)/2;
        
        LaurentPolynomial.OfDouble p1 = new OfDouble(new double[] {1, -2, 1}, -1); // (z^-1 - 2 + z)
        LaurentPolynomial.OfDouble p2 = new OfDouble(new double[] {1}, 0); // const 1
        for (int i = 1; i <= mn; i++) {
            p2 = p1.pow(i)
                    .mul(Math.pow(-4, -i) * Binomial.of(mn + i, i))
                    .add(p2);
        }
        p2 = new OfDouble(new double[] {0.5, 0.5}, -1).pow(m) // (1/2 + 1/2*z^-1)^N
                .mul(new OfDouble(new double[] {1}, (m + 1)/2)) // *z^Ceiling(N/2)
                .mul(p2);
        return p2.toArray();
    }
}
