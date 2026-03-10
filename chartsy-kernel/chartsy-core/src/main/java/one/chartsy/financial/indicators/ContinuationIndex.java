/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import one.chartsy.data.structures.DoubleWindowSummaryStatistics;
import one.chartsy.financial.AbstractDoubleIndicator;
import one.chartsy.study.ChartStudy;
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
import org.apache.commons.math3.util.FastMath;

@ChartStudy(
        name = "Continuation Index",
        label = "CI({gamma}, {order}, {length})",
        category = "Momentum",
        kind = StudyKind.INDICATOR,
        placement = StudyPlacement.OWN_PANEL
)
@StudyAxis(min = -1.0, max = 1.0, steps = {-1.0, -0.5, 0.0, 0.5, 1.0})
@StudyParameter(id = "lineColor", name = "Line Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#00695C", order = 100)
@StudyParameter(id = "lineStyle", name = "Line Style", scope = StudyParameterScope.VISUAL, type = StudyParameterType.STROKE, defaultValue = "THIN_SOLID", order = 110)
@StudyParameter(id = "zeroLineColor", name = "Zero Line Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#808080", order = 120)
@StudyParameter(id = "zeroLineStyle", name = "Zero Line Style", scope = StudyParameterScope.VISUAL, type = StudyParameterType.STROKE, defaultValue = "ULTRATHIN_DOTTED", order = 130)
@LinePlotSpec(id = "ci", label = "CI", output = "value", colorParameter = "lineColor", strokeParameter = "lineStyle", order = 10)
@HorizontalLinePlotSpec(id = "zero", label = "Zero", value = 0.0, colorParameter = "zeroLineColor", strokeParameter = "zeroLineStyle", order = 20)
public class ContinuationIndex extends AbstractDoubleIndicator {

    private final int length;
    private final UltimateSmoother smoother;
    private final LaguerreFilter laguerre;
    private final DoubleWindowSummaryStatistics diffStats;
    private double last = Double.NaN;

    @StudyFactory(input = StudyInputKind.CLOSES)
    public static ContinuationIndex study(
            @StudyParameter(id = "gamma", name = "Gamma", scope = StudyParameterScope.COMPUTATION, defaultValue = "0.8", order = 10) double gamma,
            @StudyParameter(id = "order", name = "Order", scope = StudyParameterScope.COMPUTATION, defaultValue = "8", order = 20) int order,
            @StudyParameter(id = "length", name = "Length", scope = StudyParameterScope.COMPUTATION, defaultValue = "40", order = 30) int length
    ) {
        return new ContinuationIndex(gamma, order, length);
    }

    public ContinuationIndex(double gamma, int order, int length) {
        this.length = length;
        this.smoother = new UltimateSmoother(Math.max(1, length / 2));
        this.laguerre = new LaguerreFilter(gamma, order, length);
        this.diffStats = new DoubleWindowSummaryStatistics(length);
    }

    @Override
    public void accept(double price) {
        double us = smoother.smooth(price);
        double lg = laguerre.filter(price);
        double diff = us - lg;
        diffStats.add(FastMath.abs(diff));
        double avgDiff = diffStats.getAverage();
        double ref = (avgDiff != 0.0) ? 2.0 * diff / avgDiff : 0.0;
        last = FastMath.tanh(ref);
    }

    @Override
    @StudyOutput(id = "value", name = "CI", order = 10)
    public double getLast() {
        return last;
    }

    @Override
    public boolean isReady() {
        return diffStats.getCount() >= length;
    }

    static class LaguerreFilter {
        private final int order;
        private final double gamma;
        private final UltimateSmoother smoother;
        private final double[] curr;
        private final double[] prev;
        private double last = Double.NaN;

        LaguerreFilter(double gamma, int order, int length) {
            if (gamma < 0.0 || gamma >= 1.0)
                throw new IllegalArgumentException("gamma must be in [0,1)");
            if (order < 1)
                throw new IllegalArgumentException("order must be >= 1");
            this.gamma = gamma;
            this.order = order;
            this.smoother = new UltimateSmoother(length);
            this.curr = new double[order + 1];
            this.prev = new double[order + 1];
        }

        double filter(double price) {
            System.arraycopy(curr, 0, prev, 0, curr.length);
            for (int i = 2; i <= order; i++) {
                curr[i] = -gamma * prev[i - 1] + prev[i - 1] + gamma * prev[i];
            }
            curr[1] = smoother.smooth(price);
            double fir = 0.0;
            for (int i = 1; i <= order; i++) {
                fir += curr[i];
            }
            last = fir / order;
            return last;
        }
    }
}
