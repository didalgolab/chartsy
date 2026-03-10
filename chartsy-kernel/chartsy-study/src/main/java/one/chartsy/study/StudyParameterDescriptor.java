package one.chartsy.study;

public record StudyParameterDescriptor(
        String id,
        String name,
        String description,
        StudyParameterScope scope,
        StudyParameterType type,
        Class<?> valueType,
        Class<?> enumType,
        String defaultValue,
        int order,
        StudyStereotype stereotype
) {
    public StudyParameterDescriptor {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("id is blank");
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("name is blank");
        if (description == null)
            description = "";
        if (scope == null)
            throw new IllegalArgumentException("scope is null");
        if (type == null)
            type = StudyParameterType.AUTO;
        if (valueType == null)
            valueType = Void.class;
        if (enumType == null)
            enumType = Void.class;
        if (defaultValue == null)
            defaultValue = "";
        if (stereotype == null)
            stereotype = StudyStereotype.NONE;
    }

    public Class<?> effectiveValueType() {
        if (type == StudyParameterType.ENUM && enumType != Void.class)
            return enumType;
        if (valueType != Void.class)
            return valueType;
        return switch (type) {
            case AUTO, STRING -> String.class;
            case INTEGER -> Integer.class;
            case DOUBLE -> Double.class;
            case BOOLEAN -> Boolean.class;
            case COLOR -> StudyColor.class;
            case STROKE -> String.class;
            case ENUM -> enumType != Void.class ? enumType : String.class;
        };
    }
}
