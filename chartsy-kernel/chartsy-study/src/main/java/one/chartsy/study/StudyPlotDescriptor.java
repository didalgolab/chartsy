package one.chartsy.study;

public record StudyPlotDescriptor(
        String id,
        String label,
        int order,
        StudyPlotType type,
        String outputId,
        String secondaryOutputId,
        double value1,
        double value2,
        boolean upper,
        String colorParameter,
        String secondaryColorParameter,
        String strokeParameter,
        String visibleParameter,
        StudyMarkerType marker
) {
    public StudyPlotDescriptor {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("id is blank");
        if (label == null || label.isBlank())
            throw new IllegalArgumentException("label is blank");
        if (type == null)
            throw new IllegalArgumentException("type is null");
        if (outputId == null)
            outputId = "";
        if (secondaryOutputId == null)
            secondaryOutputId = "";
        if (colorParameter == null)
            colorParameter = "";
        if (secondaryColorParameter == null)
            secondaryColorParameter = "";
        if (strokeParameter == null)
            strokeParameter = "";
        if (visibleParameter == null)
            visibleParameter = "";
        if (marker == null)
            marker = StudyMarkerType.NONE;
    }
}
