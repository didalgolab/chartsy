/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.reporting.statistics.inferential;

import one.chartsy.kernel.math.statistics.DefaultSampleSet;
import one.chartsy.kernel.math.statistics.SampleSet;
import one.chartsy.kernel.math.statistics.StandardScore;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.beans.PropertyDescriptor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Streams bootstrapped samples and builds per-property statistics
 * for all JavaBean properties of primitive type {@code double}. The collected
 * statistics are then used to standardize a fresh target sample into
 * a {@link BootstrappedEstimates} consisting of {@link StandardScore}s.
 *
 * <p>Usage:
 * <pre>
 * var est = new BootstrappedEstimator<EquitySummaryStatistics>();
 * simulations.forEach(est); // calls accept(...) for each EquitySummaryStatistics
 * BootstrappedEstimate z = est.estimate(currentStats);
 * StandardScore sharpeZ = z.get("annualSharpeRatio");
 * </pre>
 *
 * <p>Notes:<br>
 * - Only properties whose declared type is {@code double} are tracked.<br>
 * - NaN and infinite values are ignored when accumulating.<br>
 * - Standardization uses the estimator's configured confidence level.
 */
public class BootstrappedEstimator<T> implements Consumer<T> {
    /** Per-property running samples discovered via BeanWrapper. */
    private final Map<String, SampleSet> samplesByProperty = new LinkedHashMap<>();
    /** Confidence level used when producing StandardScore intervals (e.g., 0.95). */
    private final double confidenceLevel;
    /** Factory for creating new SampleSet instances. */
    private final Supplier<SampleSet> sampleSetSupplier;

    /**
     * Creates an estimator with default settings: confidenceLevel = 0.95,
     * sampleSetSupplier = DefaultSampleSet::new
     */
    public BootstrappedEstimator() {
        this(0.95, DefaultSampleSet::new);
    }

    /**
     * Creates an estimator with a custom confidence level and default SampleSet factory.
     *
     * @param confidenceLevel confidence level used for StandardScore intervals (0,1]
     */
    public BootstrappedEstimator(double confidenceLevel) {
        this(confidenceLevel, DefaultSampleSet::new);
    }

    /**
     * Creates an estimator with full customization.
     *
     * @param confidenceLevel confidence level for StandardScore intervals
     * @param sampleSetSupplier supplier that creates empty SampleSet instances on demand
     */
    public BootstrappedEstimator(double confidenceLevel, Supplier<SampleSet> sampleSetSupplier) {
        if (!(confidenceLevel > 0.0 && confidenceLevel < 1.0))
            throw new IllegalArgumentException("confidenceLevel must be in (0, 1), got " + confidenceLevel);

        this.confidenceLevel = confidenceLevel;
        this.sampleSetSupplier = Objects.requireNonNull(sampleSetSupplier, "sampleSetSupplier");
    }

    /**
     * Adds a single sample observation: inspects all JavaBean properties of type {@code double}
     * and adds each finite value to its per-property {@link SampleSet}.
     *
     * @param sample the sample to inspect
     */
    @Override
    public void accept(T sample) {
        BeanWrapper bw = new BeanWrapperImpl(sample);
        for (PropertyDescriptor pd : bw.getPropertyDescriptors()) {
            if (pd.getReadMethod() != null && isPropertySupported(pd)) {
                String name = pd.getName();
                Object valueObj = safeGet(bw, name);
                if (valueObj instanceof Number num) {
                    double value = num.doubleValue();
                    if (Double.isFinite(value))
                        samplesByProperty.computeIfAbsent(name, k -> sampleSetSupplier.get()).add(value);
                }
            }
        }
    }

    /**
     * Produces a {@link BootstrappedEstimates} by standardizing each double
     * property of the given {@code target} against the accumulated {@link SampleSet}
     * for that property. If a property has no accumulated samples yet, it will still
     * be present in the result; its score components may be NaN depending on the
     * underlying {@link SampleSet} readiness.
     *
     * @param target the target sample to standardize
     * @return an immutable estimate mapping property names to {@link StandardScore}
     */
    public BootstrappedEstimates estimate(T target) {
        Objects.requireNonNull(target, "target");
        BeanWrapper bw = new BeanWrapperImpl(target);

        Map<String, StandardScore> out = new LinkedHashMap<>();
        for (PropertyDescriptor pd : bw.getPropertyDescriptors()) {
            if (pd.getReadMethod() != null && isPropertySupported(pd)) {
                String name = pd.getName();
                Object valueObj = safeGet(bw, name);
                if (valueObj instanceof Number num) {
                    double value = num.doubleValue();

                    SampleSet samples = samplesByProperty.get(name);
                    if (samples != null && samples.isReady())
                        out.put(name, samples.standardize(value, confidenceLevel));
                }
            }
        }
        return new BootstrappedEstimates(
                Collections.unmodifiableMap(out),
                Collections.unmodifiableMap(new LinkedHashMap<>(samplesByProperty)),
                confidenceLevel
        );
    }

    protected boolean isPropertySupported(PropertyDescriptor property) {
        Class<?> type = property.getPropertyType();
        return (type == double.class || type == Double.class);
    }

    /**
     * Clears all accumulated samples.
     */
    public void reset() {
        samplesByProperty.values().forEach(SampleSet::reset);
        samplesByProperty.clear();
    }

    /**
     * @return an unmodifiable view of the current per-property samples.
     *         Keys are property names; values are the live SampleSet instances.
     */
    public final Map<String, SampleSet> samples() {
        return Collections.unmodifiableMap(samplesByProperty);
    }

    /**
     * Gives the confidence level used for standardization.
     *
     * @return the confidence level (e.g. 0.95 for a 95% interval)
     */
    public final double confidenceLevel() {
        return confidenceLevel;
    }

    /**
     * Safe property read that does not throw on underlying getter failure.
     */
    private static Object safeGet(BeanWrapper bw, String propertyName) {
        try {
            return bw.getPropertyValue(propertyName);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
