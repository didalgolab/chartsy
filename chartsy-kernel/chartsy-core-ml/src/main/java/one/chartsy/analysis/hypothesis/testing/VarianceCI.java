package one.chartsy.analysis.hypothesis.testing;

import one.chartsy.data.RealVector;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

public class VarianceCI {

    public static final double DEFAULT_CONFIDENCE_LEVEL = 0.95;

    /**
     * Calculates a 95% confidence interval (CI) for the population variance estimated
     * from the given values.
     *
     * @param values the vector of values
     * @return the {@code RealVector} containing the {@code {min, max}} values of
     *         the estimated confidence interval for the variance
     */
    public static RealVector varianceCI(RealVector values) {
        return varianceCI(values, DEFAULT_CONFIDENCE_LEVEL);
    }

    /**
     * Calculates a confidence interval (CI) for the population variance estimated
     * from the given values.
     *
     * @param values the vector of values
     * @param confidenceLevel the requested confidence level, between 0 and 1 (exclusive)
     * @return the {@code RealVector} containing the {@code {min, max}} values of
     *         the estimated confidence interval for the variance
     */
    public static RealVector varianceCI(RealVector values, double confidenceLevel) {
        if (confidenceLevel <= 0.0 || confidenceLevel >= 1.0)
            throw new IllegalArgumentException("confidenceLevel must be in range (0, 1)");

        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (int i = 0; i < values.size(); i++) {
            stats.addValue(values.get(i));
        }

        double variance = stats.getVariance();
        int n = values.size();

        // Degrees of freedom for the sample variance
        int degreesOfFreedom = n - 1;

        // Chi-Squared Distribution for the CI of Variance
        ChiSquaredDistribution chiSquaredDist = new ChiSquaredDistribution(degreesOfFreedom);
        double alpha = 1.0 - confidenceLevel;
        double chiSquaredLower = chiSquaredDist.inverseCumulativeProbability(alpha / 2);
        double chiSquaredUpper = chiSquaredDist.inverseCumulativeProbability(1.0 - alpha / 2);

        double ciLowerVariance = (degreesOfFreedom * variance) / chiSquaredUpper;
        double ciUpperVariance = (degreesOfFreedom * variance) / chiSquaredLower;

        return RealVector.fromValues(ciLowerVariance, ciUpperVariance);
    }
}