/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.internal;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DoubleSeries;
import one.chartsy.study.StudyDescriptor;
import one.chartsy.ui.chart.StudyRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StudySeriesEvaluatorTest {

    @Test
    void coercesRawUiParametersBeforeInvokingStudyFactory() {
        StudyDescriptor descriptor = StudyRegistry.getDefault().getDescriptor("one.chartsy.financial.indicators.FractalDimension");

        DoubleSeries output = StudySeriesEvaluator.evaluate(
                descriptor,
                sampleDataset(48),
                Map.of("priceBase", "CLOSE", "periods", "30")
        ).outputs().get("value");

        assertThat(output).isNotNull();
        assertThat(output.length()).isPositive();
        assertThat(hasFiniteValue(output)).isTrue();
    }

    @Test
    void fallsBackToDescriptorDefaultsWhenFactoryParametersAreMissing() {
        StudyDescriptor descriptor = StudyRegistry.getDefault().getDescriptor("one.chartsy.financial.indicators.FractalDimension");

        DoubleSeries output = StudySeriesEvaluator.evaluate(
                descriptor,
                sampleDataset(48),
                Map.of()
        ).outputs().get("value");

        assertThat(output).isNotNull();
        assertThat(output.length()).isPositive();
        assertThat(hasFiniteValue(output)).isTrue();
    }

    private static boolean hasFiniteValue(DoubleSeries series) {
        for (int index = 0; index < series.length(); index++) {
            if (Double.isFinite(series.get(index)))
                return true;
        }
        return false;
    }

    private static CandleSeries sampleDataset(int size) {
        List<Candle> candles = new ArrayList<>(size);
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int index = 0; index < size; index++) {
            double open = 100 + index * 0.8;
            double close = open + Math.sin(index / 3.0) * 2.0;
            double high = Math.max(open, close) + 1.5;
            double low = Math.min(open, close) - 1.2;
            candles.add(Candle.of(start.plusDays(index).atStartOfDay(), open, high, low, close, 1_000 + index * 25L));
        }

        return CandleSeries.of(
                SymbolResource.of(SymbolIdentity.of("TEST"), TimeFrame.Period.DAILY).withDataType(Candle.class),
                candles
        );
    }
}
