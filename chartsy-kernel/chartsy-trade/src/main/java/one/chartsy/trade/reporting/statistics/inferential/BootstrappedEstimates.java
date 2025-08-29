/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.reporting.statistics.inferential;

import one.chartsy.kernel.math.statistics.StandardScore;
import one.chartsy.kernel.math.statistics.SampleSet;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable result of standardizing all {@code double} properties of an
 * {@link EquitySummaryStatistics} instance against bootstrapped per-property
 * sample distributions. The map keys are property names (e.g. "endingEquity", "maxDrawdownPercent"),
 * and the values are {@link StandardScore}s computed at a given confidence level.
 * This type also exposes a snapshot of the underlying per-property SampleSet map
 * at the time of creation, so the caller may inspect sample counts or moments.
 */
public final class BootstrappedEstimates {

    private final Map<String, StandardScore> scoresByProperty;
    private final Map<String, SampleSet> samplesSnapshot;
    private final double confidenceLevel;

    public BootstrappedEstimates(
            Map<String, StandardScore> scoresByProperty,
            Map<String, SampleSet> samplesSnapshot,
            double confidenceLevel
    ) {
        this.scoresByProperty = Objects.requireNonNull(scoresByProperty, "scoresByProperty");
        this.samplesSnapshot = Objects.requireNonNull(samplesSnapshot, "samplesSnapshot");
        this.confidenceLevel = confidenceLevel;
    }

    /**
     * Gives the {@link StandardScore} for a property.
     *
     * @param propertyName the property name (e.g. "endingEquity", "maxDrawdownPercent")
     * @return the StandardScore for the property, or null if absent
     */
    public StandardScore get(String propertyName) {
        return scoresByProperty.get(propertyName);
    }

    /**
     * Gives an immutable view of all property scores.
     *
     * @return map of property names to StandardScore instances
     */
    public Map<String, StandardScore> asMap() {
        return scoresByProperty;
    }

    /** Returns a snapshot of the underlying per-property SampleSet instances. */
    public Map<String, SampleSet> samples() {
        return samplesSnapshot;
    }

    /**
     * Gives the confidence level used to produce these scores.
     *
     * @return the confidence level (e.g. 0.95 for a 95% interval)
     */
    public double confidenceLevel() {
        return confidenceLevel;
    }

    @Override
    public String toString() {
        return "BootstrappedEstimates{properties=" + scoresByProperty.keySet()
                + ", confidenceLevel=" + confidenceLevel + "}";
    }
}
