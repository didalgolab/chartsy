package one.chartsy.ui.chart;

import one.chartsy.study.StudyDescriptor;
import one.chartsy.study.StudyPresentationPlan;

import java.util.SequencedMap;

public interface StudyBackedChartPlugin {
    String getStudyDescriptorId();

    StudyDescriptor getStudyDescriptor();

    SequencedMap<String, Object> getStudyParameterValues();

    StudyPresentationPlan getStudyPresentationPlan();
}
