package one.chartsy.study;

public record StudyPlotDefinition(
        String id,
        String label,
        int order,
        StudyPlotType type,
        Object values,
        Object secondaryValues,
        double value1,
        double value2,
        boolean upper,
        StudyColor primaryColor,
        StudyColor secondaryColor,
        String stroke,
        boolean visible,
        StudyMarkerType marker
) {
    public StudyPlotDefinition {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("id is blank");
        if (label == null || label.isBlank())
            throw new IllegalArgumentException("label is blank");
        if (type == null)
            throw new IllegalArgumentException("type is null");
        if (stroke == null)
            stroke = "";
        if (marker == null)
            marker = StudyMarkerType.NONE;
    }

    public static StudyPlotDefinition line(String id, String label, int order, Object values,
                                           StudyColor color, String stroke, boolean visible) {
        return new StudyPlotDefinition(id, label, order, StudyPlotType.LINE, values, null,
                Double.NaN, Double.NaN, true, color, null, stroke, visible, StudyMarkerType.NONE);
    }

    public static StudyPlotDefinition histogram(String id, String label, int order, Object values,
                                                StudyColor positiveColor, StudyColor negativeColor, boolean visible) {
        return new StudyPlotDefinition(id, label, order, StudyPlotType.HISTOGRAM, values, null,
                Double.NaN, Double.NaN, true, positiveColor, negativeColor, "", visible, StudyMarkerType.NONE);
    }

    public static StudyPlotDefinition bar(String id, String label, int order, Object values,
                                          StudyColor color, boolean visible) {
        return new StudyPlotDefinition(id, label, order, StudyPlotType.BAR, values, null,
                Double.NaN, Double.NaN, true, color, null, "", visible, StudyMarkerType.NONE);
    }

    public static StudyPlotDefinition horizontal(String id, String label, int order, double value,
                                                 StudyColor color, String stroke, boolean visible) {
        return new StudyPlotDefinition(id, label, order, StudyPlotType.HORIZONTAL_LINE, null, null,
                value, Double.NaN, true, color, null, stroke, visible, StudyMarkerType.NONE);
    }

    public static StudyPlotDefinition fill(String id, String label, int order, Object values,
                                           double from, double to, boolean upper,
                                           StudyColor color, boolean visible) {
        return new StudyPlotDefinition(id, label, order, StudyPlotType.FILL, values, null,
                from, to, upper, color, null, "", visible, StudyMarkerType.NONE);
    }

    public static StudyPlotDefinition insideFill(String id, String label, int order, Object upperValues,
                                                 Object lowerValues, StudyColor color, boolean visible) {
        return new StudyPlotDefinition(id, label, order, StudyPlotType.INSIDE_FILL, upperValues, lowerValues,
                Double.NaN, Double.NaN, true, color, null, "", visible, StudyMarkerType.NONE);
    }

    public static StudyPlotDefinition shape(String id, String label, int order, Object values,
                                            StudyColor color, boolean visible, StudyMarkerType marker) {
        return new StudyPlotDefinition(id, label, order, StudyPlotType.SHAPE, values, null,
                Double.NaN, Double.NaN, true, color, null, "", visible, marker);
    }
}
