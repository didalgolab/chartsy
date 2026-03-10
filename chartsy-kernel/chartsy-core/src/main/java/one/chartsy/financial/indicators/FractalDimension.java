/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import one.chartsy.CandleField;
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
        name = "Fractal Dimension",
        label = "Fractal Dimension ({priceBase}, {periods})",
        category = "Volatility",
        kind = StudyKind.INDICATOR,
        placement = StudyPlacement.OWN_PANEL
)
@StudyAxis(min = 1.35, max = 1.65, steps = {0.2, 0.4, 0.6, 0.8, 1.0, 1.2, 1.4, 1.6, 1.8, 2.0})
@StudyParameter(id = "color", name = "Line Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#388E8E", order = 100)
@StudyParameter(id = "style", name = "Line Style", scope = StudyParameterScope.VISUAL, type = StudyParameterType.STROKE, defaultValue = "THIN_SOLID", order = 110)
@StudyParameter(id = "delimiterLineColor", name = "Delimiter Line Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#000000", order = 120)
@StudyParameter(id = "delimiterLineStyle", name = "Delimiter Line Style", scope = StudyParameterScope.VISUAL, type = StudyParameterType.STROKE, defaultValue = "ULTRATHIN_DOTTED", order = 130)
@StudyParameter(id = "insideVisibility", name = "Inside Visibility", scope = StudyParameterScope.VISUAL, type = StudyParameterType.BOOLEAN, defaultValue = "true", order = 140)
@StudyParameter(id = "insideNeutralColor", name = "Inside Neutral Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#EBF3F3", order = 150)
@StudyParameter(id = "insideHighColor", name = "Inside High Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#388E8E", order = 160)
@FillPlotSpec(id = "insideNeutral", label = "Inside Neutral", output = "value", from = 1.4, to = 1.6, upper = true, colorParameter = "insideNeutralColor", visibleParameter = "insideVisibility", order = 10)
@FillPlotSpec(id = "insideHigh", label = "Inside High", output = "value", from = 1.6, to = 1.6, upper = true, colorParameter = "insideHighColor", visibleParameter = "insideVisibility", order = 20)
@LinePlotSpec(id = "fdi", label = "FDI", output = "value", colorParameter = "color", strokeParameter = "style", order = 30)
@HorizontalLinePlotSpec(id = "level14", label = "1.4", value = 1.4, colorParameter = "delimiterLineColor", strokeParameter = "delimiterLineStyle", order = 40)
@HorizontalLinePlotSpec(id = "level16", label = "1.6", value = 1.6, colorParameter = "delimiterLineColor", strokeParameter = "delimiterLineStyle", order = 50)
public class FractalDimension extends AbstractDoubleIndicator {
    private static final double LOG2 = Math.log(2);
    private final DoubleWindowSummaryStatistics values;
    private final double DX_POW2;
    private final double LOG_2N;
    private final int periods;
    private double last = 1.5;

    @StudyFactory(input = StudyInputKind.PRICE_FIELD, inputParameter = "priceBase")
    public static FractalDimension study(
            @StudyParameter(id = "priceBase", name = "Price Field", scope = StudyParameterScope.INPUT, type = StudyParameterType.ENUM, enumType = CandleField.class, defaultValue = "CLOSE", order = 10) CandleField priceBase,
            @StudyParameter(id = "periods", name = "Periods", scope = StudyParameterScope.COMPUTATION, defaultValue = "30", order = 20) int periods
    ) {
        return new FractalDimension(periods);
    }

    public FractalDimension(int periods) {
        if (periods <= 0)
            throw new IllegalArgumentException("The periods argument " + periods + " must be positive");

        this.values = new DoubleWindowSummaryStatistics(periods);
        this.DX_POW2 = Math.pow(periods - 1.0, -2.0);
        this.LOG_2N = Math.log(2.0 * (periods - 1));
        this.periods = periods;
    }

    @Override
    public void accept(double value) {
        values.add(value);

        var max = values.getMax();
        var min = values.getMin();
        if (values.isFull() && max - min > 0.0) {
            double diffPrevious = 0.0;
            double curveLength = 0.0;
            for (int i = 0; i < periods; i++) {
                double diff = (values.get(i) - min)/(max - min);
                if (i > 0)
                    curveLength += Math.sqrt((diff - diffPrevious)*(diff - diffPrevious) + DX_POW2);
                diffPrevious = diff;
            }

            if (curveLength > 0.0)
                last = 1.0 + (Math.log(curveLength) + LOG2) / LOG_2N;
        }
    }

    @Override
    @StudyOutput(id = "value", name = "FDI", order = 10)
    public final double getLast() {
        return last;
    }

    @Override
    public boolean isReady() {
        return values.isFull();
    }
}
