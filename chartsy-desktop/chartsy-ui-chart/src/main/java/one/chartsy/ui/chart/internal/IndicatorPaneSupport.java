package one.chartsy.ui.chart.internal;

import one.chartsy.core.Range;
import one.chartsy.study.StudyAxisDescriptor;
import one.chartsy.study.StudyPlacement;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.StudyBackedChartPlugin;
import one.chartsy.ui.chart.data.VisualRange;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class IndicatorPaneSupport {
    private IndicatorPaneSupport() {
    }

    public enum Compatibility {
        COMPATIBLE,
        INCOMPATIBLE
    }

    public record PaneGroup(UUID id, List<Indicator> indicators, int order) {
        public PaneGroup {
            indicators = List.copyOf(indicators);
        }

        public PaneGroup withIndicators(List<Indicator> indicators) {
            return new PaneGroup(id, indicators, order);
        }

        public String title(int paneNumber) {
            StringBuilder title = new StringBuilder("Pane ").append(paneNumber);
            if (!indicators.isEmpty()) {
                title.append(" (");
                for (int i = 0; i < indicators.size(); i++) {
                    if (i > 0)
                        title.append(", ");
                    title.append(indicators.get(i).getLabel());
                }
                title.append(')');
            }
            return title.toString();
        }
    }

    public static List<PaneGroup> groupByPane(List<? extends Indicator> indicators) {
        LinkedHashMap<UUID, List<Indicator>> grouped = new LinkedHashMap<>();
        int order = 0;
        for (Indicator indicator : indicators) {
            if (indicator == null)
                continue;
            UUID paneId = Objects.requireNonNullElseGet(indicator.getPanelId(), UUID::randomUUID);
            grouped.computeIfAbsent(paneId, ignored -> new ArrayList<>()).add(indicator);
        }

        List<PaneGroup> panes = new ArrayList<>(grouped.size());
        for (var entry : grouped.entrySet())
            panes.add(new PaneGroup(entry.getKey(), entry.getValue(), order++));
        return List.copyOf(panes);
    }

    public static boolean isMainPanelIndicator(Indicator indicator) {
        return indicator instanceof StudyBackedChartPlugin studyPlugin
                && studyPlugin.getStudyDescriptor().placement() == StudyPlacement.MAIN_PANEL;
    }

    public static boolean isOwnPanelIndicator(Indicator indicator) {
        return indicator != null && !isMainPanelIndicator(indicator);
    }

    public static Compatibility compatibility(Indicator indicator, List<? extends Indicator> paneIndicators) {
        if (indicator == null || paneIndicators == null)
            return Compatibility.INCOMPATIBLE;
        if (!isOwnPanelIndicator(indicator))
            return Compatibility.INCOMPATIBLE;

        StudyAxisDescriptor leftAxis = axisDescriptor(indicator);
        for (Indicator existing : paneIndicators) {
            if (existing == null || existing == indicator)
                continue;
            if (!isOwnPanelIndicator(existing))
                return Compatibility.INCOMPATIBLE;

            StudyAxisDescriptor rightAxis = axisDescriptor(existing);
            if (leftAxis.logarithmic() != rightAxis.logarithmic())
                return Compatibility.INCOMPATIBLE;
            if (!sameOrEmpty(leftAxis.steps(), rightAxis.steps()))
                return Compatibility.INCOMPATIBLE;
            if (!sameOrMissing(leftAxis.min(), rightAxis.min()))
                return Compatibility.INCOMPATIBLE;
            if (!sameOrMissing(leftAxis.max(), rightAxis.max()))
                return Compatibility.INCOMPATIBLE;
        }
        return Compatibility.COMPATIBLE;
    }

    public static boolean isCompatiblePane(List<? extends Indicator> paneIndicators) {
        for (Indicator indicator : paneIndicators) {
            if (compatibility(indicator, paneIndicators) == Compatibility.INCOMPATIBLE)
                return false;
        }
        return true;
    }

    public static VisualRange combinedRange(List<? extends Indicator> indicators, ChartContext context) {
        Range.Builder includedRange = new Range.Builder();
        Range.Builder fallbackRange = new Range.Builder();
        boolean anyIncluded = false;
        boolean logarithmic = true;
        double fixedMin = Double.NaN;
        double fixedMax = Double.NaN;
        boolean conflictingMin = false;
        boolean conflictingMax = false;

        for (Indicator indicator : indicators) {
            if (indicator == null)
                continue;

            VisualRange indicatorRange = indicator.getRange(context);
            Range range = indicatorRange.range();
            if (range != null && !range.isEmpty()) {
                fallbackRange.add(range.min()).add(range.max());
                if (axisDescriptor(indicator).includeInRange()) {
                    includedRange.add(range.min()).add(range.max());
                    anyIncluded = true;
                }
            }

            logarithmic &= indicatorRange.isLogarithmic();

            StudyAxisDescriptor axis = axisDescriptor(indicator);
            if (axis.hasFixedMin()) {
                if (Double.isNaN(fixedMin))
                    fixedMin = axis.min();
                else if (Double.compare(fixedMin, axis.min()) != 0)
                    conflictingMin = true;
            }
            if (axis.hasFixedMax()) {
                if (Double.isNaN(fixedMax))
                    fixedMax = axis.max();
                else if (Double.compare(fixedMax, axis.max()) != 0)
                    conflictingMax = true;
            }
        }

        Range baseRange = (anyIncluded ? includedRange : fallbackRange).toRange();
        if (baseRange.isEmpty())
            baseRange = Range.of(0.0, 1.0);

        Range.Builder merged = new Range.Builder().add(baseRange.min()).add(baseRange.max());
        if (!conflictingMin && !Double.isNaN(fixedMin))
            merged.add(fixedMin);
        if (!conflictingMax && !Double.isNaN(fixedMax))
            merged.add(fixedMax);
        Range finalRange = merged.toRange();
        if (finalRange.isEmpty())
            finalRange = baseRange;

        return new VisualRange(finalRange, logarithmic);
    }

    public static StudyAxisDescriptor axisDescriptor(Indicator indicator) {
        if (indicator instanceof StudyBackedChartPlugin studyPlugin) {
            var plan = studyPlugin.getStudyPresentationPlan();
            if (plan != null && !plan.plots().isEmpty())
                return plan.axis();
            return studyPlugin.getStudyDescriptor().axis();
        }
        return indicator.getPresentationPlan().axis();
    }

    private static boolean sameOrMissing(double left, double right) {
        return Double.isNaN(left) || Double.isNaN(right) || Double.compare(left, right) == 0;
    }

    private static boolean sameOrEmpty(double[] left, double[] right) {
        if (left == null || left.length == 0 || right == null || right.length == 0)
            return true;
        if (left.length != right.length)
            return false;
        for (int i = 0; i < left.length; i++) {
            if (Double.compare(left[i], right[i]) != 0)
                return false;
        }
        return true;
    }
}
