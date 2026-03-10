/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import one.chartsy.data.CandleSeries;
import one.chartsy.financial.ValueIndicatorSupport;
import one.chartsy.study.ChartStudy;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

@ChartStudy(
        name = "Sfora",
        label = "Sfora",
        category = "Bands",
        kind = StudyKind.OVERLAY,
        placement = StudyPlacement.MAIN_PANEL,
        implementation = FramaTrendWhispers.class,
        builder = SforaOverlayStudy.Builder.class
)
@StudyParameter(id = "color", name = "Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#AFEEEE", order = 100)
@StudyParameter(id = "stroke", name = "Stroke", scope = StudyParameterScope.VISUAL, type = StudyParameterType.STROKE, defaultValue = "THIN_SOLID", order = 110)
public final class SforaOverlayStudy {

    @StudyFactory(input = StudyInputKind.CANDLES)
    public static FramaTrendWhispers study(
            @StudyParameter(id = "numberOfEnvelops", name = "Number of Envelops", scope = StudyParameterScope.COMPUTATION, defaultValue = "8", order = 10) int numberOfEnvelops,
            @StudyParameter(id = "slowdownPeriod", name = "Slowdown Period", scope = StudyParameterScope.COMPUTATION, defaultValue = "16", order = 20) int slowdownPeriod,
            @StudyParameter(id = "framaPeriod", name = "FRAMA Period", scope = StudyParameterScope.COMPUTATION, defaultValue = "45", order = 30) int framaPeriod
    ) {
        return new FramaTrendWhispers(new FramaTrendWhispers.Options(numberOfEnvelops, framaPeriod, slowdownPeriod));
    }

    public static final class Builder implements StudyPresentationBuilder {
        @Override
        public StudyPresentationPlan build(StudyPresentationContext context, StudyPresentationPlan defaultPlan) {
            CandleSeries quotes = context.dataset(CandleSeries.class);
            int numberOfEnvelops = StudyBuilderSupport.intParameter(context, "numberOfEnvelops");
            int slowdownPeriod = StudyBuilderSupport.intParameter(context, "slowdownPeriod");
            int framaPeriod = StudyBuilderSupport.intParameter(context, "framaPeriod");

            var options = new FramaTrendWhispers.Options(numberOfEnvelops, framaPeriod, slowdownPeriod);
            @SuppressWarnings("unchecked")
            var indicatorPaths = (ToDoubleFunction<FramaTrendWhispers>[]) new ToDoubleFunction[numberOfEnvelops];
            for (int index = 0; index < numberOfEnvelops; index++) {
                int pathNumber = index;
                indicatorPaths[index] = indicator -> indicator.getPath(pathNumber).getLast();
            }

            var paths = ValueIndicatorSupport.calculate(quotes, new FramaTrendWhispers(options), indicatorPaths);
            List<StudyPlotDefinition> plots = new ArrayList<>(numberOfEnvelops);
            for (int index = 0; index < numberOfEnvelops; index++) {
                plots.add(StudyPlotDefinition.line(
                        "path" + (index + 1),
                        Integer.toString(index + 1),
                        (index + 1) * 10,
                        paths.get(index),
                        StudyBuilderSupport.colorParameter(context, "color"),
                        StudyBuilderSupport.strokeParameter(context, "stroke"),
                        true
                ));
            }
            return new StudyPresentationPlan(defaultPlan.axis(), plots);
        }
    }
}
