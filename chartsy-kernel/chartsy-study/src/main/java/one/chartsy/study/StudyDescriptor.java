package one.chartsy.study;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.SequencedMap;

public record StudyDescriptor(
        String id,
        String name,
        String label,
        String category,
        StudyKind kind,
        StudyPlacement placement,
        Class<?> definitionType,
        Class<?> implementationType,
        StudyFactoryDescriptor factory,
        SequencedMap<String, StudyParameterDescriptor> parameters,
        SequencedMap<String, StudyOutputDescriptor> outputs,
        StudyAxisDescriptor axis,
        List<StudyPlotDescriptor> plots,
        Class<? extends StudyPresentationBuilder> builderType
) {
    public StudyDescriptor {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("id is blank");
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("name is blank");
        if (label == null || label.isBlank())
            throw new IllegalArgumentException("label is blank");
        if (category == null || category.isBlank())
            throw new IllegalArgumentException("category is blank");
        if (kind == null)
            throw new IllegalArgumentException("kind is null");
        if (placement == null)
            throw new IllegalArgumentException("placement is null");
        if (definitionType == null)
            throw new IllegalArgumentException("definitionType is null");
        if (implementationType == null)
            throw new IllegalArgumentException("implementationType is null");
        if (factory == null)
            throw new IllegalArgumentException("factory is null");

        var orderedParameters = new LinkedHashMap<String, StudyParameterDescriptor>();
        if (parameters != null)
            orderedParameters.putAll(parameters);
        parameters = Collections.unmodifiableSequencedMap(orderedParameters);

        var orderedOutputs = new LinkedHashMap<String, StudyOutputDescriptor>();
        if (outputs != null)
            orderedOutputs.putAll(outputs);
        outputs = Collections.unmodifiableSequencedMap(orderedOutputs);

        axis = axis == null ? new StudyAxisDescriptor() : axis;
        plots = plots == null ? List.of() : List.copyOf(plots);
        builderType = builderType == null ? StudyPresentationBuilder.class : builderType;
    }

    public StudyParameterDescriptor parameter(String parameterId) {
        return parameters.get(parameterId);
    }

    public StudyOutputDescriptor output(String outputId) {
        return outputs.get(outputId);
    }

    public boolean hasCustomBuilder() {
        return !Objects.equals(builderType, StudyPresentationBuilder.class);
    }
}
