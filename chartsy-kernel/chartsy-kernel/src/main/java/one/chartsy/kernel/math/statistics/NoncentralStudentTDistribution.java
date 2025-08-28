/*
 * Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.kernel.math.statistics;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.special.Beta;
import org.apache.commons.math3.special.Gamma;

/**
 * Numerically stable CDF for the noncentral t distribution.
 *
 * <p>Implements the Poisson-mixture representation:
 *
 * <pre>
 *   For t >= 0:
 *     F_{nu,delta}(t) = Phi(-delta)
 *                       + 0.5 * sum_{j=0..inf} [ p_j I_y(j+1/2, nu/2) + q_j I_y(j+1, nu/2) ],
 *     where y = t^2 / (t^2 + nu),
 *       p_j = exp(-m) m^j / j!,            m = delta^2 / 2,
 *       q_j = (delta / sqrt(2)) exp(-m) m^j / Gamma(j + 3/2),
 *     and I_y is the regularized incomplete beta.
 *
 *   For t < 0, uses the symmetry F(t; nu, delta) = 1 - F(-t; nu, -delta).
 * </pre>
 *
 * <p>References:<br>
 *  - Benton, D., Krishnamoorthy, K. (2003). Computing discrete mixtures of continuous distributions:
 *    noncentral chisquare, noncentral t and ... CSDA 43(2), 249-267.<br>
 *  - Boost implementation notes for noncentral t (uses the same anchored Poisson strategy).<br>
 *  - Wikipedia "Noncentral t-distribution" (series with p_j and q_j in terms of regularized I_y).<br>
 */
final class NoncentralStudentTDistribution {

    private NoncentralStudentTDistribution() { }

    // Global numerical knobs.
    private static final double BETA_EPS = 1e-14;
    private static final int    BETA_MAX_ITERS = 100000;

    // Tolerance for truncating the Poisson series; relative to running sum.
    private static final double SERIES_REL_TOL = 1e-15;
    private static final int    SERIES_HARD_CAP = 200000;

    private static final NormalDistribution N01 = new NormalDistribution(0.0, 1.0);

    /**
     * CDF of noncentral t at t, with degrees of freedom nu and noncentrality delta.
     * nu may be non-integer but must be > 0. Returns NaN on invalid inputs.
     */
    public static double cdf(double t, double nu, double delta) {
        if (!(nu > 0.0) || Double.isNaN(t) || Double.isNaN(delta))
            return Double.NaN;
        if (!Double.isFinite(t))
            return t < 0.0 ? 0.0 : 1.0;
        if (delta == 0.0)
            return new TDistribution(nu).cumulativeProbability(t);

        boolean flip = false;
        double tt = t;
        double del = delta;
        if (t < 0.0) {
            flip = true;
            tt = -t;
            del = -delta;
        }

        final double y = (tt == 0.0) ? 0.0 : (tt * tt) / (tt * tt + nu);
        double series = anchoredPoissonSeries(y, nu, del);
        double out = N01.cumulativeProbability(-del) + 0.5 * series;
        if (out < 0.0)
            out = 0.0;
        if (out > 1.0)
            out = 1.0;

        return flip ? (1.0 - out) : out;
    }

    /** Anchored Poisson series around the mode j0 = floor(m) where m = delta^2/2. */
    private static double anchoredPoissonSeries(double y, double nu, double delta) {
        if (y <= 0.0)
            return 0.0;

        final double b = 0.5 * nu;               // second shape of I_y(., b)
        final double m = 0.5 * delta * delta;    // Poisson mean
        final int j0 = (int) Math.max(0, Math.floor(m));
        final double logP0 = -m + j0 * Math.log(m) - Gamma.logGamma(j0 + 1.0);
        final double logQ0Abs = Math.log(Math.abs(delta)) - 0.5 * Math.log(2.0) - m + j0 * Math.log(m) - Gamma.logGamma(j0 + 1.5);

        double p = (m == 0.0 && j0 == 0) ? 1.0 : Math.exp(logP0);
        double q = Math.exp(logQ0Abs);
        if (delta < 0.0)
            q = -q;

        double sum = 0.0;

        sum += p * regIncBetaStable(y, j0 + 0.5, b);
        sum += q * regIncBetaStable(y, j0 + 1.0, b);

        {
            double pj = p, qj = q;
            for (int j = j0 + 1, k = 0; k < SERIES_HARD_CAP; ++j, ++k) {
                pj *= (m / j);
                qj *= m / (j + 0.5);

                double term = pj * regIncBetaStable(y, j + 0.5, b) + qj * regIncBetaStable(y, j + 1.0, b);
                sum += term;

                if (Math.abs(term) <= SERIES_REL_TOL * (1.0 + Math.abs(sum)))
                    break;
            }
        }

        if (j0 > 0 && m > 0.0) {
            double pj = p, qj = q;
            for (int j = j0 - 1, k = 0; j >= 0 && k < SERIES_HARD_CAP; --j, ++k) {
                pj *= (j + 1.0) / m;
                qj *= (j + 1.5) / m;

                double term = pj * regIncBetaStable(y, j + 0.5, b) + qj * regIncBetaStable(y, j + 1.0, b);
                sum += term;

                if (Math.abs(term) <= SERIES_REL_TOL * (1.0 + Math.abs(sum)))
                    break;
            }
        }

        return sum;
    }

    /** Regularized incomplete beta with a stable evaluation near x = 1. */
    private static double regIncBetaStable(double x, double a, double b) {
        if (x <= 0.0)
            return 0.0;
        if (x >= 1.0)
            return 1.0;

        if (x > 0.5) {
            double tail = Beta.regularizedBeta(1.0 - x, b, a, BETA_EPS, BETA_MAX_ITERS);
            double res = 1.0 - tail;
            if (res < 0.0)
                res = 0.0;
            if (res > 1.0)
                res = 1.0;
            return res;
        }
        return Beta.regularizedBeta(x, a, b, BETA_EPS, BETA_MAX_ITERS);
    }
}
