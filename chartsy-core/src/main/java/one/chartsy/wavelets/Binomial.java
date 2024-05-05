/* Copyright 2018 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.wavelets;

/**
 * Represents the binomial coefficient.
 * <p>
 * The binomial coefficient is indexed by a pair of integers {@code (n, k)}. It
 * is the coefficient of the <i>x<sup>k</sup></i> term in the polynomial
 * expansion of the expression <i>(1 + x)<sup>n</sup></i>. The binomial
 * coefficient is sometimes called as "N choose K" because it's the number of
 * combinations of {@code n} items taken {@code k} at a time.
 * 
 * 
 * @author Mariusz Bernacki
 */
public class Binomial {
    
    /**
     * Computes the binomial coefficient indexed by the given pair of integers
     * {@code n >= k >= 0}.
     * <p>
     * The result may be interpreted as a number of combinations of {@code n}
     * items taken {@code k} at a time. The time complexity of the algorithm is
     * <i>min(O(k), O(n-k))</i>
     * 
     * @param n
     *            the number of possible choices
     * @param k
     *            the number of selected choices
     * @return the binomial coefficient
     * @throws ArithmeticException if the result overflows a long
     */
    public static long of(int n, int k) {
        if (n < 0)
            throw new IllegalArgumentException("n cannot be negative");
        if (k < 0)
            throw new IllegalArgumentException("n cannot be negative");
        if (n < k)
            throw new IllegalArgumentException("(n < k) is not supported");
        
        if (k == 0)
            return 1;
        if (k > n/2)
            return Binomial.of(n, n - k);

        long result = 1;
        for (int i = 1; i <= k; i++)
            result = Math.multiplyExact(result, (long)n - k + i) / i;

        return result;
    }
}
