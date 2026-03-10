/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import one.chartsy.Candle;
import one.chartsy.financial.AbstractCandleIndicator;
import one.chartsy.study.ChartStudy;
import one.chartsy.study.LinePlotSpec;
import one.chartsy.study.StudyFactory;
import one.chartsy.study.StudyInputKind;
import one.chartsy.study.StudyKind;
import one.chartsy.study.StudyOutput;
import one.chartsy.study.StudyParameter;
import one.chartsy.study.StudyParameterScope;
import one.chartsy.study.StudyParameterType;
import one.chartsy.study.StudyPlacement;

@ChartStudy(
        name = "FRAMA, Leading",
        label = "FRAMA, Leading",
        category = "Trend",
        kind = StudyKind.OVERLAY,
        placement = StudyPlacement.MAIN_PANEL
)
@StudyParameter(id = "color", name = "Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#66CC00", order = 100)
@StudyParameter(id = "stroke", name = "Stroke", scope = StudyParameterScope.VISUAL, type = StudyParameterType.STROKE, defaultValue = "DEFAULT", order = 110)
@LinePlotSpec(id = "frama", label = "FRAMA", output = "value", colorParameter = "color", strokeParameter = "stroke", order = 10)
public class FramaZero extends AbstractCandleIndicator {
    public static int DEFAULT_FRAMA_PERIOD = 45;
    public static int DEFAULT_ATR_PERIOD = 15;
    private final Frama frama;
    private final AverageTrueRange atr;
    private double last;

    @StudyFactory(input = StudyInputKind.CANDLES)
    public static FramaZero study(
            @StudyParameter(id = "leadingPeriods", name = "Leading Periods", scope = StudyParameterScope.COMPUTATION, defaultValue = "45", order = 10) int leadingPeriods
    ) {
        return new FramaZero(leadingPeriods);
    }

    public FramaZero() {
        this(DEFAULT_FRAMA_PERIOD, DEFAULT_ATR_PERIOD);
    }

    public FramaZero(int framaPeriods) {
        this(framaPeriods, DEFAULT_ATR_PERIOD);
    }

    public FramaZero(int framaPeriods, int atrPeriods) {
        this.frama = new Frama(framaPeriods);
        this.atr = new AverageTrueRange(atrPeriods);
    }

    @Override
    public void accept(Candle bar) {
        frama.accept(bar.close());
        atr.accept(bar);
        last = frama.getLast() + atr.getLast();
    }

    @Override
    @StudyOutput(id = "value", name = "FRAMA", order = 10)
    public double getLast() {
        return last;
    }

    @Override
    public boolean isReady() {
        return frama.isReady() && atr.isReady();
    }
}
