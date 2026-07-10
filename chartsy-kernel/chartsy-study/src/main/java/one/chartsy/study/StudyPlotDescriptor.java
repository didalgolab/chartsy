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
        boolean visibleByDefault,
        StudyMarkerType marker
) {
    public static final int INHERITED_PANEL_ID = -1;
    public static final String RESULT_VISIBILITY_PARAMETER_ID = visibilityParameterId("result");
    public static final String RESULT_PANEL_PARAMETER_ID = panelParameterId("result");

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
        if (marker == null)
            marker = StudyMarkerType.NONE;
    }

    public static String visibilityParameterId(String plotId) {
        if (plotId == null || plotId.isBlank())
            throw new IllegalArgumentException("plotId is blank");
        return "plot." + plotId + ".visible";
    }

    public static String panelParameterId(String plotId) {
        if (plotId == null || plotId.isBlank())
            throw new IllegalArgumentException("plotId is blank");
        return "plot." + plotId + ".panel";
    }

    public String visibilityParameterId() {
        return visibilityParameterId(id);
    }

    public String panelParameterId() {
        return panelParameterId(id);
    }
}
