/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial;

import one.chartsy.data.DoubleSeries;
import one.chartsy.data.Series;
import one.chartsy.data.packed.PackedDoubleSeries;
import one.chartsy.time.Chronological;
import one.chartsy.time.Timeline;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.ToDoubleFunction;

/**
 * Low-level utility methods for creating and manipulating financial indicators.
 *
 * <p>This class is mostly for library writers presenting indicator views
 * of data series; most static indicator calculations intended for end users are in
 * the various {@code Dataset} and {@code Series} classes.
 *
 */
public final class ValueIndicatorSupport {

    public static <VI extends ValueIndicator.OfDouble & DoubleConsumer>
    PackedDoubleSeries calculate(DoubleSeries data, VI indicator) {
        return calculate(data, indicator, ValueIndicator.OfDouble::getLast);
    }

    private static PackedDoubleSeries createSeries(double[] values, Timeline timeline) {
        return (values == null)
                ? DoubleSeries.empty(timeline)
                : DoubleSeries.of(values, timeline);
    }

    public static <V extends ValueIndicator & DoubleConsumer>
    PackedDoubleSeries calculate(DoubleSeries data, V indicator, ToDoubleFunction<V> output) {
        double[] values = null;

        for (int index = data.length(); --index >= 0; ) {
            indicator.accept(data.get(index));

            if (values == null && indicator.isReady())
                values = new double[index + 1];
            if (values != null)
                values[index] = output.applyAsDouble(indicator);
        }
        return createSeries(values, data.getTimeline());
    }

    public static <E extends Chronological, V extends ValueIndicator.OfDouble & Consumer<E>>
    PackedDoubleSeries calculate(Series<E> data, V indicator) {
        return calculate(data, indicator, ValueIndicator.OfDouble::getLast);
    }

    public static <E extends Chronological, V extends ValueIndicator & Consumer<E>>
    PackedDoubleSeries calculate(Series<E> data, V indicator, ToDoubleFunction<V> output) {
        double[] values = null;

        for (int index = data.length(); --index >= 0; ) {
            indicator.accept(data.get(index));

            if (values == null && indicator.isReady())
                values = new double[index + 1];
            if (values != null)
                values[index] = output.applyAsDouble(indicator);
        }
        return createSeries(values, data.getTimeline());
    }

    public static <E extends Chronological, V extends ValueIndicator & Consumer<E>>
    List<PackedDoubleSeries> calculate(Series<E> data, V indicator, ToDoubleFunction<V>... outputs) {
        double[][] values = new double[outputs.length][];

        for (int index = data.length() - 1; index >= 0; index--) {
            indicator.accept(data.get(index));

            for (int out = 0; out < outputs.length; out++) {
                double outputValue = outputs[out].applyAsDouble(indicator);
                if (values[out] == null && !Double.isNaN(outputValue)) {
                    values[out] = new double[index + 1];
                }
                if (values[out] != null) {
                    values[out][index] = outputValue;
                }
            }
        }

        return Arrays.stream(values)
                .map(v -> (v == null)
                        ? DoubleSeries.empty(data.getTimeline())
                        : DoubleSeries.of(v, data.getTimeline()))
                .toList();
    }

    public static <V extends ValueIndicator & DoubleConsumer>
    List<PackedDoubleSeries> calculate(DoubleSeries data, V indicator, ToDoubleFunction<V>... outputs) {
        double[][] values = new double[outputs.length][];

        for (int index = data.length() - 1; index >= 0; index--) {
            indicator.accept(data.get(index));

            for (int out = 0; out < outputs.length; out++) {
                double outputValue = outputs[out].applyAsDouble(indicator);
                if (values[out] == null && !Double.isNaN(outputValue)) {
                    values[out] = new double[index + 1];
                }
                if (values[out] != null) {
                    values[out][index] = outputValue;
                }
            }
        }

        return Arrays.stream(values)
                .map(v -> (v == null)
                        ? DoubleSeries.empty(data.getTimeline())
                        : DoubleSeries.of(v, data.getTimeline()))
                .toList();
    }
}
