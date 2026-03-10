/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import one.chartsy.data.CandleSeries;
import one.chartsy.financial.ValueIndicatorSupport;
import one.chartsy.study.ChartStudy;
import one.chartsy.study.StudyAxis;
import one.chartsy.study.StudyColor;
import one.chartsy.study.StudyFactory;
import one.chartsy.study.StudyInputKind;
import one.chartsy.study.StudyKind;
import one.chartsy.study.StudyParameter;
import one.chartsy.study.StudyParameterScope;
import one.chartsy.study.StudyParameterType;
import one.chartsy.study.StudyPlacement;
import one.chartsy.study.StudyPlotDefinition;
import one.chartsy.study.StudyPresentationBuilder;
import one.chartsy.study.StudyPresentationContext;
import one.chartsy.study.StudyPresentationPlan;

import java.util.List;

@ChartStudy(
        name = "Sfora, Width",
        label = "Sfora, Width",
        category = "Volatility",
        kind = StudyKind.INDICATOR,
        placement = StudyPlacement.OWN_PANEL,
        implementation = FramaTrendWhispers.class,
        builder = SforaWidthStudy.Builder.class
)
@StudyAxis(logarithmic = true)
@StudyParameter(id = "rpma1Color", name = "RPMA %1 Line Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#000000", order = 100)
@StudyParameter(id = "rpma2Color", name = "RPMA %2 Line Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#006400", order = 110)
@StudyParameter(id = "atrPeriods", name = "ATR Periods", scope = StudyParameterScope.COMPUTATION, defaultValue = "60", order = 10)
@StudyParameter(id = "atrColor", name = "ATR Line Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#FF0000", order = 120)
@StudyParameter(id = "style", name = "Line Style", scope = StudyParameterScope.VISUAL, type = StudyParameterType.STROKE, defaultValue = "DEFAULT", order = 130)
@StudyParameter(id = "numberOfEnvelops", name = "Number of Envelops", scope = StudyParameterScope.COMPUTATION, defaultValue = "8", order = 20)
@StudyParameter(id = "slowdownPeriod", name = "Slowdown Period", scope = StudyParameterScope.COMPUTATION, defaultValue = "16", order = 30)
@StudyParameter(id = "framaPeriod", name = "FRAMA Period", scope = StudyParameterScope.COMPUTATION, defaultValue = "45", order = 40)
public final class SforaWidthStudy {

    @StudyFactory(input = StudyInputKind.CANDLES)
    public static FramaTrendWhispers study(
            @StudyParameter(id = "atrPeriods", name = "ATR Periods", scope = StudyParameterScope.COMPUTATION, defaultValue = "60", order = 10) int atrPeriods,
            @StudyParameter(id = "numberOfEnvelops", name = "Number of Envelops", scope = StudyParameterScope.COMPUTATION, defaultValue = "8", order = 20) int numberOfEnvelops,
            @StudyParameter(id = "slowdownPeriod", name = "Slowdown Period", scope = StudyParameterScope.COMPUTATION, defaultValue = "16", order = 30) int slowdownPeriod,
            @StudyParameter(id = "framaPeriod", name = "FRAMA Period", scope = StudyParameterScope.COMPUTATION, defaultValue = "45", order = 40) int framaPeriod
    ) {
        return new FramaTrendWhispers(new FramaTrendWhispers.Options(numberOfEnvelops, framaPeriod, slowdownPeriod));
    }

    public static final class Builder implements StudyPresentationBuilder {
        @Override
        public StudyPresentationPlan build(StudyPresentationContext context, StudyPresentationPlan defaultPlan) {
            CandleSeries quotes = context.dataset(CandleSeries.class);
            int atrPeriods = StudyBuilderSupport.intParameter(context, "atrPeriods");
            int numberOfEnvelops = StudyBuilderSupport.intParameter(context, "numberOfEnvelops");
            int slowdownPeriod = StudyBuilderSupport.intParameter(context, "slowdownPeriod");
            int framaPeriod = StudyBuilderSupport.intParameter(context, "framaPeriod");

            var atr1 = quotes.atr(atrPeriods).sma(atrPeriods);
            var atr2 = atr1.mul(1.1);
            var atrHalf = atr1.mul(0.5);
            var atrDouble = atr1.mul(2.0);
            var wildersHalf = quotes.closes().wilders(atrPeriods).sma(atrPeriods).mul(0.5);

            var options = new FramaTrendWhispers.Options(numberOfEnvelops, framaPeriod, slowdownPeriod);
            var width = ValueIndicatorSupport.calculate(quotes, new FramaTrendWhispers(options), FramaTrendWhispers::getRange);

            StudyColor atrColor = StudyBuilderSupport.colorParameter(context, "atrColor");
            String style = StudyBuilderSupport.strokeParameter(context, "style");
            return new StudyPresentationPlan(defaultPlan.axis(), List.of(
                    StudyPlotDefinition.line("atrLower", "ATR%60 L", 10, atr1, atrColor, style, true),
                    StudyPlotDefinition.line("atrUpper", "ATR%60 H", 20, atr2, atrColor, style, true),
                    StudyPlotDefinition.line("atrHalf", "ATR%60 /2", 30, atrHalf, atrColor, "DOTTED", true),
                    StudyPlotDefinition.line("atrDouble", "ATR%60 *2", 40, atrDouble, atrColor, "DOTTED", true),
                    StudyPlotDefinition.line("atrWildersHalf", "ATR%60 2/2", 50, wildersHalf, StudyColor.fromRgb(0x000000), "DOTTED", true),
                    StudyPlotDefinition.line("width", "RPMA %1", 60, width, StudyBuilderSupport.colorParameter(context, "rpma1Color"), style, true)
            ));
        }
    }
}
