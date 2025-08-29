/*
 * Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.kernel.math.statistics;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class StandardScoreEstimatorTest {

    private static final double[] VALUES = {10.2, 9.8, 10.5, 9.9, 10.1, 10.4, 9.7};
    private static final double TARGET = 11.0;
    private static final double CL = 0.95;

    final StandardScoreEstimator estimator = StandardScoreEstimator.getInstance();

    private static SampleSetSnapshot make(double... xs) {
        var s = new DefaultSampleSet();
        s.addAll(xs);
        return s.snapshot();
    }

    private static void approx(double a, double b, double tol, String what) {
        assertTrue(Double.isFinite(a) && Double.isFinite(b), "Non-finite in " + what);
        assertTrue(Math.abs(a - b) <= tol, what + " got " + a + " expected " + b + " tol " + tol);
    }

    @Test
    void deterministicFixture() {
        var zs = make(VALUES);
        var r = estimator.estimate(TARGET, zs, CL);

        double meanExp = 10.085714285714285;
        double sdExp   = 0.30237157840738177;
        double ciLoExp = 1.1897905203285808;
        double ciHiExp = 4.831664444855651;

        approx(zs.mean(), meanExp, 1e-12, "mean");
        approx(zs.sampleStandardDeviation(), sdExp, 1e-12, "sd");
        approx(r.normalConfidenceInterval().min(), ciLoExp, 5e-4, "CI lower");
        approx(r.normalConfidenceInterval().max(), ciHiExp, 5e-4, "CI upper");
    }

    @Test
    void shiftInvariance() {
        var a = make(VALUES);
        var ra = estimator.estimate(TARGET, a, CL);

        double c = 2.0;
        double[] shifted = Arrays.stream(VALUES).map(x -> x + c).toArray();
        var b = make(shifted);
        var rb = estimator.estimate(TARGET + c, b, CL);

        approx(ra.score(), rb.score(), 1e-12, "z_hat shift");
        approx(ra.normalConfidenceInterval().min(), rb.normalConfidenceInterval().min(), 1e-6, "CI lower shift");
        approx(ra.normalConfidenceInterval().max(), rb.normalConfidenceInterval().max(), 1e-6, "CI upper shift");
    }

    @Test
    void scaleInvariance() {
        var a = make(VALUES);
        var ra = estimator.estimate(TARGET, a, CL);

        double s = 3.0;
        double[] scaled = Arrays.stream(VALUES).map(x -> s * x).toArray();
        var b = make(scaled);
        var rb = estimator.estimate(s * TARGET, b, CL);

        approx(ra.score(), rb.score(), 1e-12, "z_hat scale");
        approx(ra.normalConfidenceInterval().min(), rb.normalConfidenceInterval().min(), 1e-6, "CI lower scale");
        approx(ra.normalConfidenceInterval().max(), rb.normalConfidenceInterval().max(), 1e-6, "CI upper scale");
    }

    @Test
    void monotonicityInTarget() {
        var z1 = make(VALUES);
        var r1 = estimator.estimate(10.5, z1, CL);

        var z2 = make(VALUES);
        var r2 = estimator.estimate(11.0, z2, CL);

        assertTrue(r2.score() > r1.score(), "z_hat should increase");
        assertTrue(r2.normalConfidenceInterval().min() > r1.normalConfidenceInterval().min(), "CI lower should increase");
        assertTrue(r2.normalConfidenceInterval().max() > r1.normalConfidenceInterval().max(), "CI upper should increase");
    }

    @Test
    void symmetryAtZeroT() {
        var tmp = make(VALUES);
        double sampleMean = tmp.mean();

        var zs = make(VALUES);
        var r = estimator.estimate(sampleMean, zs, CL);

        double s = r.normalConfidenceInterval().max();
        double sym = Math.abs((r.normalConfidenceInterval().max() + r.normalConfidenceInterval().min()) / Math.max(1.0, Math.abs(s)));
        assertTrue(sym <= 1e-8, "CI not symmetric about 0");
    }

    @Test
    void inversionIdentity() {
        // Monotonicity in delta for fixed t > 0
        double g1 = NoncentralStudentTDistribution.cdf(8.0, 6, -5.0);
        double g2 = NoncentralStudentTDistribution.cdf(8.0, 6,  0.0);
        double g3 = NoncentralStudentTDistribution.cdf(8.0, 6,  5.0);
        assertTrue(g1 > g2 && g2 > g3, "CDF not decreasing in delta");
    }

    @Test
    @Tag("slow")
    void monteCarloCoverage() {
        final long seed = 123456789L;
        final Random rng = new Random(seed);

        final double mu = 1.0;
        final double sigma = 2.0;
        final double target = 4.0;
        final int n = 10;
        final double CL = 0.95;
        final int trials = 100;

        int covered = 0;
        double trueTheta = (target - mu) / sigma;

        for (int k = 0; k < trials; ++k) {
            var zs = new DefaultSampleSet();
            for (int i = 0; i < n; ++i)
                zs.add(mu + sigma * rng.nextGaussian());

            StandardScore r = estimator.estimate(target, zs.snapshot(), CL);
            if (r.normalConfidenceInterval().min() <= trueTheta && trueTheta <= r.normalConfidenceInterval().max())
                covered++;
        }
        double coverage = covered / (double) trials;
        assertTrue(coverage >= 0.93 && coverage <= 0.97,
                "Empirical coverage " + coverage + " outside [0.93, 0.97]");
    }
}
