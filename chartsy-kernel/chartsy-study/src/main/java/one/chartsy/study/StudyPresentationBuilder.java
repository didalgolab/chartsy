package one.chartsy.study;

public interface StudyPresentationBuilder {
    default StudyPresentationPlan build(StudyPresentationContext context, StudyPresentationPlan defaultPlan) {
        return defaultPlan;
    }
}
