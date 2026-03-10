package one.chartsy.study;

public record StudyOutputDescriptor(
        String id,
        String name,
        String description,
        int order,
        StudyMemberTarget target,
        String memberName,
        Class<?> valueType
) {
    public StudyOutputDescriptor {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("id is blank");
        if (name == null)
            name = "";
        if (description == null)
            description = "";
        if (target == null)
            throw new IllegalArgumentException("target is null");
        if (memberName == null || memberName.isBlank())
            throw new IllegalArgumentException("memberName is blank");
        if (valueType == null)
            valueType = Double.class;
    }
}
