package one.chartsy.study;

import java.util.Comparator;
import java.util.List;

public record StudyPresentationPlan(
        StudyAxisDescriptor axis,
        List<StudyPlotDefinition> plots
) {
    public StudyPresentationPlan {
        axis = axis == null ? new StudyAxisDescriptor() : axis;
        plots = plots == null ? List.of() : plots.stream()
                .sorted(Comparator.comparingInt(StudyPlotDefinition::order).thenComparing(StudyPlotDefinition::label))
                .toList();
    }

    public static StudyPresentationPlan empty(StudyAxisDescriptor axis) {
        return new StudyPresentationPlan(axis, List.of());
    }
}
