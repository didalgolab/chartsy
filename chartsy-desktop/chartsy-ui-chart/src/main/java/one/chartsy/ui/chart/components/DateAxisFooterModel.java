/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import one.chartsy.TimeFrameHelper;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.Scale;
import one.chartsy.charting.financial.AdaptiveCategoryTimeSteps;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.ChartData;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;

final class DateAxisFooterModel {
    private DateAxisFooterModel() {
    }

    static SharedDateAxisFooter.FooterSnapshot buildStatic(ChartContext chartFrame,
                                                           Rectangle plotBounds,
                                                           Scale timeScale) {
        ChartData chartData = chartFrame.getChartData();
        if (chartData == null || !chartData.hasDataset() || plotBounds == null || plotBounds.isEmpty()) {
            return new SharedDateAxisFooter.FooterSnapshot(
                    (plotBounds == null) ? new Rectangle() : new Rectangle(plotBounds),
                    List.of(),
                    List.of(),
                    null,
                    null
            );
        }

        DataInterval visibleRange = resolveVisibleRange(chartFrame, chartData, timeScale);
        AdaptiveCategoryTimeSteps steps = resolveAdaptiveSteps(timeScale);
        var upperTicks = buildUpperTicks(plotBounds, visibleRange, steps);
        LowerLane lowerLane = buildLowerLane(chartData, plotBounds, visibleRange, steps, timeScale);
        return new SharedDateAxisFooter.FooterSnapshot(
                new Rectangle(plotBounds),
                List.copyOf(upperTicks),
                List.copyOf(lowerLane.ticks()),
                lowerLane.contextLabel(),
                null
        );
    }

    static SharedDateAxisFooter.HoverLabel buildHoverLabel(ChartData chartData,
                                                           Rectangle plotBounds,
                                                           DataInterval visibleRange,
                                                           int hoverSlot) {
        if (hoverSlot < 0)
            return null;

        int x = projectValueX(plotBounds, visibleRange, hoverSlot);
        long epochMicros = chartData.getSlotTime(hoverSlot);
        String label = TimeFrameHelper.formatDate(chartData.getTimeFrame(), epochMicros);
        return new SharedDateAxisFooter.HoverLabel(hoverSlot, epochMicros, x, label);
    }

    static DataInterval resolveVisibleRange(ChartContext chartFrame, ChartData chartData, Scale timeScale) {
        if (chartFrame.getMainPanel() != null
                && chartFrame.getMainPanel().getStackPanel() != null
                && chartFrame.getMainPanel().getStackPanel().getChartPanel() != null) {
            var mainChart = chartFrame.getMainPanel().getStackPanel().getChartPanel().getEngineChart();
            if (mainChart != null && mainChart.getXAxis() != null) {
                DataInterval visibleRange = mainChart.getXAxis().getVisibleRange();
                if (visibleRange != null && !visibleRange.isEmpty())
                    return visibleRange;
            }
        }
        if (timeScale != null && timeScale.getAxis() != null) {
            DataInterval visibleRange = timeScale.getAxis().getVisibleRange();
            if (visibleRange != null && !visibleRange.isEmpty())
                return visibleRange;
        }
        return new DataInterval(chartData.getViewportMinX(), chartData.getViewportMaxX());
    }

    static AdaptiveCategoryTimeSteps resolveAdaptiveSteps(Scale timeScale) {
        if (timeScale != null && timeScale.getStepsDefinition() instanceof AdaptiveCategoryTimeSteps steps)
            return steps;
        return null;
    }

    private static List<SharedDateAxisFooter.TickMark> buildUpperTicks(Rectangle plotBounds,
                                                                       DataInterval visibleRange,
                                                                       AdaptiveCategoryTimeSteps steps) {
        if (visibleRange == null || visibleRange.isEmpty() || steps == null)
            return List.of();

        List<AdaptiveCategoryTimeSteps.TickValue> upperTicks = steps.snapshotUpperTicks(
                visibleRange,
                Math.max(1, plotBounds.width)
        );
        if (upperTicks.isEmpty())
            return List.of();

        List<SharedDateAxisFooter.TickMark> ticks = new ArrayList<>(upperTicks.size());
        for (AdaptiveCategoryTimeSteps.TickValue tick : upperTicks) {
            int x = projectValueX(plotBounds, visibleRange, tick.value());
            if (x < plotBounds.x - 1 || x > plotBounds.x + plotBounds.width + 1)
                continue;
            ticks.add(new SharedDateAxisFooter.TickMark(tick.value(), x, x + 4, tick.label(), false));
        }
        return ticks;
    }

    private static LowerLane buildLowerLane(ChartData chartData,
                                            Rectangle plotBounds,
                                            DataInterval visibleRange,
                                            AdaptiveCategoryTimeSteps steps,
                                            Scale timeScale) {
        int visibleStart = Math.max(0, (int) Math.ceil((visibleRange != null) ? visibleRange.getMin() : chartData.getViewportMinX()));
        int visibleEnd = Math.max(
                visibleStart + 1,
                Math.min(chartData.getHistoricalSlotCount(), (int) Math.ceil((visibleRange != null) ? visibleRange.getMax() : chartData.getViewportMaxX()))
        );
        if (visibleStart >= visibleEnd)
            return LowerLane.empty();

        int firstSlot = visibleStart;
        int lastSlot = visibleEnd - 1;
        long firstTime = chartData.getSlotTime(firstSlot);
        long lastTime = chartData.getSlotTime(lastSlot);
        AdaptiveCategoryTimeSteps.LanePlan plan = (steps != null && visibleRange != null && !visibleRange.isEmpty())
                ? steps.plan(visibleRange, Math.max(1, plotBounds.width))
                : AdaptiveCategoryTimeSteps.plan(firstTime, lastTime, observedBaseUnitMillis(timeScale, chartData));
        AdaptiveCategoryTimeSteps.Lane level = plan.lower();
        if (level == AdaptiveCategoryTimeSteps.Lane.NONE)
            return LowerLane.empty();
        boolean compactDay = level == AdaptiveCategoryTimeSteps.Lane.DAY && plan.upper() == AdaptiveCategoryTimeSteps.Lane.MONTH;

        List<SharedDateAxisFooter.TickMark> ticks = new ArrayList<>();
        String firstKey = AdaptiveCategoryTimeSteps.key(level, firstTime);
        ticks.add(new SharedDateAxisFooter.TickMark(
                firstSlot,
                plotBounds.x,
                plotBounds.x + 4,
                AdaptiveCategoryTimeSteps.format(level, firstTime, compactDay),
                true
        ));
        String previousKey = firstKey;

        for (int slot = firstSlot + 1; slot < visibleEnd; slot++) {
            long slotTime = chartData.getSlotTime(slot);
            String currentKey = AdaptiveCategoryTimeSteps.key(level, slotTime);
            if (currentKey.equals(previousKey))
                continue;

            int x = projectValueX(plotBounds, visibleRange, slot);
            ticks.add(new SharedDateAxisFooter.TickMark(
                    slot,
                    x,
                    x + 4,
                    AdaptiveCategoryTimeSteps.format(level, slotTime, compactDay),
                    false
            ));
            previousKey = currentKey;
        }
        return new LowerLane(List.copyOf(ticks), null);
    }

    private static double observedBaseUnitMillis(Scale timeScale, ChartData chartData) {
        if (timeScale != null && timeScale.getStepsDefinition() instanceof AdaptiveCategoryTimeSteps steps)
            return steps.baseUnitMillis();
        return chartData.getTimeFrame() != null && chartData.getTimeFrame().getAsSeconds().isPresent()
                ? chartData.getTimeFrame().getAsSeconds().orElseThrow().getAmount() * 1000.0d
                : one.chartsy.charting.TimeUnit.DAY.getMillis();
    }

    private static int projectValueX(Rectangle plotBounds,
                                     DataInterval visibleRange,
                                     double value) {
        if (plotBounds == null || plotBounds.isEmpty() || visibleRange == null || visibleRange.isEmpty())
            return (plotBounds != null) ? plotBounds.x : 0;
        double visibleSpan = Math.max(1.0d, visibleRange.getLength());
        double projectorSpan = Math.max(1.0d, plotBounds.width - 1.0d);
        return plotBounds.x + (int) Math.round((value - visibleRange.getMin()) * projectorSpan / visibleSpan);
    }

    private record LowerLane(List<SharedDateAxisFooter.TickMark> ticks,
                             SharedDateAxisFooter.ContextLabel contextLabel) {

        private static LowerLane empty() {
            return new LowerLane(List.of(), null);
        }
    }
}
