package one.chartsy.study;

import java.util.List;

public record StudyFactoryDescriptor(
        StudyInputKind inputKind,
        String inputParameter,
        StudyFactoryTarget target,
        String memberName,
        List<String> parameterIds
) {
    public StudyFactoryDescriptor {
        if (inputKind == null)
            throw new IllegalArgumentException("inputKind is null");
        if (inputParameter == null)
            inputParameter = "";
        if (target == null)
            throw new IllegalArgumentException("target is null");
        if (memberName == null || memberName.isBlank())
            throw new IllegalArgumentException("memberName is blank");
        parameterIds = parameterIds == null ? List.of() : List.copyOf(parameterIds);
    }
}
