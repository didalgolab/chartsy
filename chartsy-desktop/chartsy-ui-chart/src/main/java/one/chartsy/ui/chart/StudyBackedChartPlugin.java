package one.chartsy.ui.chart;

import one.chartsy.study.StudyDescriptor;
import one.chartsy.study.StudyPlotDescriptor;
import one.chartsy.study.StudyPresentationPlan;

import java.util.List;
import java.util.SequencedMap;

public interface StudyBackedChartPlugin extends ChartPluginPlotSource {
    String getStudyDescriptorId();

    StudyDescriptor getStudyDescriptor();

    SequencedMap<String, Object> getStudyParameterValues();

    StudyPresentationPlan getStudyPresentationPlan();

    @Override
    default List<PlotDescriptor> getPlotDescriptors() {
        List<PlotDescriptor> plots = getStudyDescriptor().plots().stream()
                .map(plot -> new PlotDescriptor(
                        plot.id(),
                        plot.label(),
                        plot.colorParameter(),
                        plot.strokeParameter(),
                        plot.visibilityParameterId(),
                        plot.panelParameterId()))
                .toList();
        if (!plots.isEmpty())
            return plots;
        return List.of(new PlotDescriptor(
                "result",
                "Result",
                "",
                "",
                StudyPlotDescriptor.RESULT_VISIBILITY_PARAMETER_ID,
                StudyPlotDescriptor.RESULT_PANEL_PARAMETER_ID));
    }

    @Override
    default String getPlotLabel(String plotId) {
        return getStudyPresentationPlan().plots().stream()
                .filter(plot -> plot.id().equals(plotId))
                .map(plot -> plot.label())
                .findFirst()
                .orElseGet(() -> ChartPluginPlotSource.super.getPlotLabel(plotId));
    }
}
