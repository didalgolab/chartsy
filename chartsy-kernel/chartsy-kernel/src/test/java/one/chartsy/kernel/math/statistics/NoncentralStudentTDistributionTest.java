/*
 * Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.kernel.math.statistics;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.TDistribution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class NoncentralStudentTDistributionTest {

    // Tolerances chosen to be strict but realistic for double precision.
    // For central cases and t=0 identities, we expect very tight agreement.
    private static final double TIGHT_EPS = 1e-12;
    private static final double LOOSE_EPS = 5e-11;

    /** Convenience alias for the method under test. */
    private static double cdf(double t, double nu, double delta) {
        return NoncentralStudentTDistribution.cdf(t, nu, delta);
    }

    // -----------------------------
    // Invalid inputs and edge cases
    // -----------------------------

    @Test
    void cdf_gives_NaN_when_nu_is_nonpositive() {
        assertTrue(Double.isNaN(cdf(0.0, 0.0, 0.0)));
        assertTrue(Double.isNaN(cdf(1.0, -1.0, 2.0)));
        assertTrue(Double.isNaN(cdf(-2.0, -0.5, 0.3)));
    }

    @Test
    void cdf_gives_NaN_when_t_or_delta_is_NaN() {
        assertTrue(Double.isNaN(cdf(Double.NaN, 10.0, 0.0)));
        assertTrue(Double.isNaN(cdf(0.0, 10.0, Double.NaN)));
    }

    @Test
    void cdf_gives_0_or_1_when_t_is_infinite() {
        assertEquals(0.0, cdf(Double.NEGATIVE_INFINITY, 5.0, 1.0));
        assertEquals(1.0, cdf(Double.POSITIVE_INFINITY, 5.0, -1.0));
    }

    // -----------------------------------
    // Identities: central case and t = 0
    // -----------------------------------

    @ParameterizedTest
    @MethodSource("centralGrid")
    void cdf_matches_central_t_distribution_when_delta_is_zero(double t, double nu) {
        double expected = new TDistribution(nu).cumulativeProbability(t);
        double actual = cdf(t, nu, 0.0);
        assertEquals(expected, actual, TIGHT_EPS,
                "Central t CDF mismatch at t=" + t + ", nu=" + nu);
    }

    static Stream<Arguments> centralGrid() {
        double[] ts = new double[] { -4.0, -2.0, -1.0, 0.0, 1.0, 2.0, 4.0 };
        double[] nus = new double[] { 0.5, 1.0, 2.0, 5.0, 10.0, 30.0, 100.0 };
        List<Arguments> out = new ArrayList<>();
        for (double t : ts) {
            for (double nu : nus) {
                out.add(Arguments.of(t, nu));
            }
        }
        return out.stream();
    }

    @ParameterizedTest
    @MethodSource("tZeroDeltaAny")
    void cdf_gives_Phi_neg_delta_when_t_is_zero(double nu, double delta) {
        double expected = new NormalDistribution(0.0, 1.0).cumulativeProbability(-delta);
        double actual = cdf(0.0, nu, delta);
        assertEquals(expected, actual, TIGHT_EPS,
                "Phi(-delta) mismatch at nu=" + nu + ", delta=" + delta);
    }

    static Stream<Arguments> tZeroDeltaAny() {
        double[] nus = new double[] { 0.5, 1.0, 2.0, 5.0, 10.0, 100.0, 1_000_000.0 };
        double[] deltas = new double[] { -3.0, -1.0, -0.1, 0.0, 0.1, 1.0, 3.0 };
        List<Arguments> out = new ArrayList<>();
        for (double nu : nus) {
            for (double delta : deltas) {
                out.add(Arguments.of(nu, delta));
            }
        }
        return out.stream();
    }

    @ParameterizedTest
    @MethodSource("centralSymmetryGrid")
    void cdf_is_anti_symmetric_around_half_when_delta_is_zero(double t, double nu) {
        double p = cdf(t, nu, 0.0);
        double q = cdf(-t, nu, 0.0);
        assertEquals(1.0, p + q, TIGHT_EPS,
                "Central t symmetry failed at t=" + t + ", nu=" + nu);
    }

    static Stream<Arguments> centralSymmetryGrid() {
        double[] ts = new double[] { -3.0, -1.0, -0.5, 0.5, 1.0, 3.0 };
        double[] nus = new double[] { 1.0, 2.0, 5.0, 10.0, 30.0 };
        List<Arguments> out = new ArrayList<>();
        for (double t : ts) {
            for (double nu : nus) {
                out.add(Arguments.of(t, nu));
            }
        }
        return out.stream();
    }

    // ----------------------------
    // Symmetry and monotonicities
    // ----------------------------

    @ParameterizedTest
    @MethodSource("randomSymmetryCases")
    void cdf_does_satisfy_symmetry_identity_when_flipping_t_and_delta(double t, double nu, double delta) {
        double p = cdf(t, nu, delta);
        double q = cdf(-t, nu, -delta);
        assertEquals(1.0, p + q, LOOSE_EPS,
                "Symmetry identity failed at t=" + t + ", nu=" + nu + ", delta=" + delta);
    }

    static Stream<Arguments> randomSymmetryCases() {
        Random rng = new Random(12345L);
        List<Arguments> out = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            double t = -5.0 + 10.0 * rng.nextDouble();
            double nu = 0.5 + 50.0 * rng.nextDouble();
            double delta = -5.0 + 10.0 * rng.nextDouble();
            out.add(Arguments.of(t, nu, delta));
        }
        return out.stream();
    }

    @Test
    void cdf_is_monotone_non_decreasing_in_t_when_nu_and_delta_are_fixed() {
        double nu = 15.0;
        double delta = 1.0;
        double[] ts = new double[] { -5.0, -2.0, -1.0, 0.0, 0.2, 0.5, 1.0, 2.0, 5.0 };
        double prev = Double.NEGATIVE_INFINITY;
        for (double t : ts) {
            double p = cdf(t, nu, delta);
            assertTrue(Double.isFinite(p));
            if (prev != Double.NEGATIVE_INFINITY) {
                // Allow microscopic numerical wiggle.
                assertTrue(p + 1e-15 >= prev, "Non-monotone at t=" + t + ", prev=" + prev + ", p=" + p);
            }
            prev = p;
        }
    }

    @Test
    void cdf_is_monotone_decreasing_in_delta_when_t_and_nu_are_fixed() {
        double t = 1.5;
        double nu = 15.0;
        double[] deltas = new double[] { -3.0, -1.0, 0.0, 1.0, 3.0 };
        double prev = Double.POSITIVE_INFINITY;
        for (double delta : deltas) {
            double p = cdf(t, nu, delta);
            assertTrue(Double.isFinite(p));
            if (prev != Double.POSITIVE_INFINITY) {
                // As delta increases, the CDF at fixed t should decrease.
                assertTrue(p <= prev + 1e-15, "Non-monotone at delta=" + delta + ", prev=" + prev + ", p=" + p);
            }
            prev = p;
        }
    }

    // ----------------------------
    // Value range and robustness
    // ----------------------------

    @Test
    void cdf_returns_values_in_unit_interval_when_scanned_over_a_grid() {
        double[] ts = new double[] { -10, -5, -2, -1, -0.1, 0.0, 0.1, 1, 2, 5, 10 };
        double[] nus = new double[] { 0.5, 1, 2, 5, 10, 30 };
        double[] deltas = new double[] { -5, -2, -1, -0.5, 0, 0.5, 1, 2, 5 };

        for (double t : ts) {
            for (double nu : nus) {
                for (double delta : deltas) {
                    double p = cdf(t, nu, delta);
                    assertTrue(Double.isFinite(p), "Non-finite at t=" + t + ", nu=" + nu + ", delta=" + delta);
                    assertTrue(p >= 0.0 - 1e-15 && p <= 1.0 + 1e-15,
                            "Out-of-range p=" + p + " at t=" + t + ", nu=" + nu + ", delta=" + delta);
                }
            }
        }
    }

    @Test
    void cdf_does_handle_extreme_deltas_without_nan_or_infinite() {
        double[] deltas = new double[] { -50.0, -30.0, 30.0, 50.0 };
        double[] ts = new double[] { -2.0, 0.0, 2.0 };
        double nu = 10.0;

        for (double delta : deltas) {
            for (double t : ts) {
                double p = cdf(t, nu, delta);
                assertTrue(Double.isFinite(p), "Non-finite at t=" + t + ", delta=" + delta);
                assertTrue(p >= 0.0 && p <= 1.0, "Out-of-range at t=" + t + ", delta=" + delta);
            }
        }
    }

    @Test
    void cdf_does_stay_close_to_one_when_t_is_huge_and_nu_is_small() {
        double t = 50.0;
        double nu = 2.0;
        double delta = 1.0;

        double p = cdf(t, nu, delta);

        // For nu=2 the central CDF is F(t) = 0.5 + t/(2*sqrt(t*t + 2)).
        // At t=50 this is about 0.9998004. With delta>0, the noncentral CDF
        // must be <= central at the same t (mass shifts right).
        assertTrue(p > 0.999, "Expected a CDF near 1 for very large t; got " + p);

        double pCentral = new org.apache.commons.math3.distribution.TDistribution(nu).cumulativeProbability(t);
        assertTrue(p <= pCentral + 1e-15,
                "For delta>0 the noncentral CDF should not exceed the central case. central=" + pCentral + ", noncentral=" + p);
    }

    @Test
    void cdf_does_approach_one_when_t_is_enormous_or_df_is_large() {
        double result;
        assertTrue((result = cdf(1388, 2.0, 1.0)) > 0.999999, "; got " + result);
        assertTrue((result = cdf(50.0, 30.0, 1.0)) > 0.999999, "; got " + result);
    }

    // ---------------------------------------
    // Large-nu normal approximation sanity
    // ---------------------------------------

    @ParameterizedTest
    @MethodSource("largeNuCases")
    void cdf_does_approach_normal_cdf_when_nu_is_large(double t, double delta) {
        double nu = 1_000_000.0;
        double actual = cdf(t, nu, delta);
        double approx = new NormalDistribution(0.0, 1.0).cumulativeProbability(t - delta);
        // Allow a modest tolerance for approximation.
        assertEquals(approx, actual, 5e-6,
                "Large-nu normal approximation mismatch at t=" + t + ", delta=" + delta);
    }

    static Stream<Arguments> largeNuCases() {
        double[] ts = new double[] { -2.0, -1.0, 0.0, 1.0, 2.0 };
        double[] deltas = new double[] { -1.0, 0.0, 0.5, 1.0, 2.0 };
        List<Arguments> out = new ArrayList<>();
        for (double t : ts) {
            for (double delta : deltas) {
                out.add(Arguments.of(t, delta));
            }
        }
        return out.stream();
    }

    // ---------------------------------------
    // Reference values: diverse spot checks
    // ---------------------------------------

    @ParameterizedTest
    @MethodSource("referenceValues")
    void cdf_matches_reference_values_for_diverse_cases(double t, double nu, double delta, double expected) {
        double actual = cdf(t, nu, delta);
        assertEquals(expected, actual, LOOSE_EPS,
                "Reference mismatch at t=" + t + ", nu=" + nu + ", delta=" + delta);
    }

    static Stream<Arguments> referenceValues() {
        // High-precision references (e.g., from a high-precision implementation)
        // Values below are rounded to ~15-16 digits for double comparisons.
        return Stream.of(
                Arguments.of(-2.0, 10.0,  2.0, 0.0001330560820659612),
                Arguments.of( 2.0, 10.0,  2.0, 0.48097315281790718),
                Arguments.of( 0.0, 10.0,  2.0, 0.022750131948179207),
                Arguments.of( 0.0,  5.0, -1.5, 0.93319279873114193),
                Arguments.of( 1.0, 30.0,  1.5, 0.30706654863753728),
                Arguments.of( 3.0,  5.0,  0.5, 0.96176081197112658),
                Arguments.of(-1.0,  2.0, -0.5, 0.36553353756765653),
                Arguments.of( 5.0,  5.0,  3.0, 0.82972541434879979),
                Arguments.of( 1.0,  1.0,  0.0, 0.75),
                Arguments.of( 2.5, 20.0, -1.0, 0.99940548831913740),
                Arguments.of(-0.5, 100.0, 2.0, 0.006258952516850297),
                Arguments.of( 0.5,  1.0,  2.0, 0.063276887479946751),
                Arguments.of( 4.0,  3.0,  1.0, 0.93609698625517888),
                Arguments.of(-3.0, 30.0, -2.0, 0.18151205001204043)
        );
    }

    // -------------------
    // Simple smoke checks
    // -------------------

    @Test
    @DisplayName("cdf_gives_exact_half_when_t_and_delta_are_zero")
    void cdf_gives_exact_half_when_t_and_delta_are_zero() {
        double p = cdf(0.0, 10.0, 0.0);
        assertEquals(0.5, p, TIGHT_EPS);
    }
}
