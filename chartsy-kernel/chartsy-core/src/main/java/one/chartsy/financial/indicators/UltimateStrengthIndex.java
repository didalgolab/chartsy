/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import one.chartsy.data.structures.DoubleWindowSummaryStatistics;
import one.chartsy.financial.AbstractDoubleIndicator;
import one.chartsy.study.ChartStudy;
import one.chartsy.study.FillPlotSpec;
import one.chartsy.study.HorizontalLinePlotSpec;
import one.chartsy.study.LinePlotSpec;
import one.chartsy.study.StudyAxis;
import one.chartsy.study.StudyFactory;
import one.chartsy.study.StudyInputKind;
import one.chartsy.study.StudyKind;
import one.chartsy.study.StudyOutput;
import one.chartsy.study.StudyParameter;
import one.chartsy.study.StudyParameterScope;
import one.chartsy.study.StudyParameterType;
import one.chartsy.study.StudyPlacement;

@ChartStudy(
        name = "Ultimate Strength Index",
        label = "USI ({length})",
        category = "Momentum",
        kind = StudyKind.INDICATOR,
        placement = StudyPlacement.OWN_PANEL
)
@StudyAxis(min = -1.0, max = 1.0, steps = {-1.0, -0.75, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0})
@StudyParameter(id = "lineColor", name = "Line Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#0066FF", order = 100)
@StudyParameter(id = "lineStyle", name = "Line Style", scope = StudyParameterScope.VISUAL, type = StudyParameterType.STROKE, defaultValue = "THIN_SOLID", order = 110)
@StudyParameter(id = "zeroLineColor", name = "Zero Line Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#000000", order = 120)
@StudyParameter(id = "zeroLineStyle", name = "Zero Line Style", scope = StudyParameterScope.VISUAL, type = StudyParameterType.STROKE, defaultValue = "ULTRATHIN_DOTTED", order = 130)
@StudyParameter(id = "fillVisibility", name = "Fill Visibility", scope = StudyParameterScope.VISUAL, type = StudyParameterType.BOOLEAN, defaultValue = "true", order = 140)
@StudyParameter(id = "bullishColor", name = "Bullish Zone Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#00CCFFCC", order = 150)
@StudyParameter(id = "bearishColor", name = "Bearish Zone Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#00FFCCCC", order = 160)
@FillPlotSpec(id = "fillBullish", label = "Bullish Zone", output = "value", from = 0.0, to = 1.0, upper = true, colorParameter = "bullishColor", visibleParameter = "fillVisibility", order = 10)
@FillPlotSpec(id = "fillBearish", label = "Bearish Zone", output = "value", from = -1.0, to = 0.0, upper = true, colorParameter = "bearishColor", visibleParameter = "fillVisibility", order = 20)
@LinePlotSpec(id = "usi", label = "USI", output = "value", colorParameter = "lineColor", strokeParameter = "lineStyle", order = 30)
@HorizontalLinePlotSpec(id = "zero", label = "Zero", value = 0.0, colorParameter = "zeroLineColor", strokeParameter = "zeroLineStyle", order = 40)
public class UltimateStrengthIndex extends AbstractDoubleIndicator {
    private final int length;
    private final DoubleWindowSummaryStatistics strengthUp;
    private final DoubleWindowSummaryStatistics strengthDown;
    private final UltimateSmoother usuSmoother;
    private final UltimateSmoother usdSmoother;
    private double lastClose = Double.NaN;
    private double last = Double.NaN;
    private int count;

    @StudyFactory(input = StudyInputKind.CLOSES)
    public static UltimateStrengthIndex study(
            @StudyParameter(id = "length", name = "Length", scope = StudyParameterScope.COMPUTATION, defaultValue = "28", order = 10) int length
    ) {
        return new UltimateStrengthIndex(length);
    }

    public UltimateStrengthIndex(int length) {
        this.length = length;
        this.strengthUp = new DoubleWindowSummaryStatistics(4);
        this.strengthDown = new DoubleWindowSummaryStatistics(4);
        this.usuSmoother = new UltimateSmoother(length);
        this.usdSmoother = new UltimateSmoother(length);
    }

    @Override
    public void accept(double close) {
        if (!Double.isNaN(lastClose)) {
            double diff = close - lastClose;
            strengthUp.add(diff > 0 ? diff : 0);
            strengthDown.add(diff < 0 ? -diff : 0);

            if (count >= 3) {
                double suAvg = strengthUp.getAverage();
                double sdAvg = strengthDown.getAverage();
                double usu = usuSmoother.smooth(suAvg);
                double usd = usdSmoother.smooth(sdAvg);
                if (usu + usd != 0.0 && usu > 0.0001 && usd > 0.0001)
                    last = (usu - usd) / (usu + usd);
            }
            count++;
        }
        lastClose = close;
    }

    @Override
    @StudyOutput(id = "value", name = "USI", order = 10)
    public double getLast() {
        return last;
    }

    @Override
    public boolean isReady() {
        return count >= length;
    }
}
