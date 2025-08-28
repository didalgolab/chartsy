/*
 * Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.kernel.math.statistics;

import one.chartsy.core.Range;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.solvers.BrentSolver;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = StandardScoreEstimator.class)
public class StandardScoreEstimator {

    public static StandardScoreEstimator getInstance() {
        return Lookup.getDefault().lookup(StandardScoreEstimator.class);
    }

    public StandardScore estimate(double value, SampleSetSnapshot stats, double confidenceLevel) {
        if (!(confidenceLevel > 0.0 && confidenceLevel < 1.0))
            throw new IllegalArgumentException("confidenceLevel must be in (0,1)");

        final long count = stats.count();
        final double s = stats.sampleStandardDeviation();
        if (count < 2 || s <= 0.0)
            throw new IllegalStateException("Need at least stats.count >= 2 and stats.stddev > 0 before computing results.");

        final long df = count - 1L;
        final double zHat = (value - stats.mean()) / s;
        final double tObs = Math.sqrt((double) stats.count()) * (value - stats.mean()) / s;

        final double alpha = 1.0 - confidenceLevel;
        final double targetUpper = 1.0 - alpha / 2.0;  // maps to lower delta
        final double targetLower = alpha / 2.0;        // maps to upper delta

        // Tunables
        final double solverAbs = 1e-10;
        final double solverRel = 1e-12;
        final int solverMaxEval = 2000;

        double deltaLo = invertForDelta(tObs, df, targetUpper, solverAbs, solverRel, solverMaxEval);
        double deltaHi = invertForDelta(tObs, df, targetLower, solverAbs, solverRel, solverMaxEval);

        double zLo = Math.min(deltaLo, deltaHi) / Math.sqrt(count);
        double zHi = Math.max(deltaLo, deltaHi) / Math.sqrt(count);

        return new StandardScore.Of(value, zHat, Range.of(zLo, zHi), stats);
    }

    private static double invertForDelta(final double tObs,
                                         final long df,
                                         final double targetCdf,
                                         final double solverAbs,
                                         final double solverRel,
                                         final int solverMaxEval) {
        final UnivariateFunction g = delta -> NoncentralStudentTDistribution.cdf(tObs, df, delta) - targetCdf;

        // Normal approximation for a good initial guess:
        // F_nct(t; df, delta) ~ Phi(t - delta) -> delta0 = t - Phi^{-1}(targetCdf)
        NormalDistribution N01 = new NormalDistribution();
        double zq = N01.inverseCumulativeProbability(targetCdf);
        double delta0 = tObs - zq;

        // Start with a local bracket around delta0, then expand geometrically if needed.
        double width = 8.0; // start small; will expand if no sign change
        double lo = delta0 - width, hi = delta0 + width;
        double flo = g.value(lo), fhi = g.value(hi);

        int expand = 0;
        while (flo * fhi > 0.0 && expand < 20) {
            width *= 2.0;
            lo = delta0 - width;
            hi = delta0 + width;
            flo = g.value(lo);
            fhi = g.value(hi);
            expand++;
        }

        if (flo * fhi > 0.0) {
            // Last resort: fall back to very wide bracket
            lo = -1e6; hi = 1e6;
            flo = g.value(lo); fhi = g.value(hi);
            if (flo * fhi > 0.0) {
                // If still no sign change, return the closer endpoint to the target.
                return Math.abs(flo) <= Math.abs(fhi) ? lo : hi;
            }
        }

        BrentSolver solver = new BrentSolver(solverRel, solverAbs);
        return solver.solve(solverMaxEval, g, lo, hi);
    }
}
