package one.chartsy.ui.chart.internal;

import one.chartsy.study.StudyAxisDescriptor;
import one.chartsy.study.StudyPlacement;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.StudyBackedChartPlugin;

import java.util.List;

public final class IndicatorPaneSupport {
    private static final int FIRST_OWN_PANEL_ID = 1;

    private IndicatorPaneSupport() {
    }

    public static int nextPanelId(List<? extends Indicator> indicators) {
        int nextPanelId = FIRST_OWN_PANEL_ID;
        for (Indicator indicator : indicators) {
            if (indicator != null && isOwnPanelIndicator(indicator))
                nextPanelId = Math.max(nextPanelId, indicator.getPanelId() + 1);
        }
        return nextPanelId;
    }

    public static boolean isMainPanelIndicator(Indicator indicator) {
        return indicator instanceof StudyBackedChartPlugin studyPlugin
                && studyPlugin.getStudyDescriptor().placement() == StudyPlacement.MAIN_PANEL;
    }

    public static boolean isOwnPanelIndicator(Indicator indicator) {
        return indicator != null && !isMainPanelIndicator(indicator);
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
}
