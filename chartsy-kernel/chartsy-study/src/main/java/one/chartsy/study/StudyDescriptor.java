package one.chartsy.study;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedMap;
import java.util.Set;

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

        builderType = builderType == null ? StudyPresentationBuilder.class : builderType;

        var orderedParameters = new LinkedHashMap<String, StudyParameterDescriptor>();
        if (parameters != null)
            orderedParameters.putAll(parameters);

        plots = plots == null ? List.of() : List.copyOf(plots);
        addPlotVisualParameters(plots, orderedParameters);
        if (plots.isEmpty() && builderType != StudyPresentationBuilder.class) {
            addVisibilityParameter(
                    orderedParameters, StudyPlotDescriptor.RESULT_VISIBILITY_PARAMETER_ID, "Result", true);
            addPanelParameter(
                    orderedParameters, StudyPlotDescriptor.RESULT_PANEL_PARAMETER_ID, "Result");
            requireBooleanVisibilityParameter(
                    StudyPlotDescriptor.RESULT_VISIBILITY_PARAMETER_ID, orderedParameters);
            requireIntegerPanelParameter(
                    StudyPlotDescriptor.RESULT_PANEL_PARAMETER_ID, orderedParameters);
        }

        parameters = Collections.unmodifiableSequencedMap(orderedParameters);

        var orderedOutputs = new LinkedHashMap<String, StudyOutputDescriptor>();
        if (outputs != null)
            orderedOutputs.putAll(outputs);
        outputs = Collections.unmodifiableSequencedMap(orderedOutputs);

        axis = axis == null ? new StudyAxisDescriptor() : axis;

        validateFactory(factory, parameters);
        validatePlots(plots, parameters, outputs);
    }

    public StudyParameterDescriptor parameter(String parameterId) {
        return parameters.get(parameterId);
    }

    public StudyOutputDescriptor output(String outputId) {
        return outputs.get(outputId);
    }

    public boolean hasCustomBuilder() {
        return builderType != StudyPresentationBuilder.class;
    }

    private static void validateFactory(StudyFactoryDescriptor factory, SequencedMap<String, StudyParameterDescriptor> parameters) {
        for (String parameterId : factory.parameterIds()) {
            if (!parameters.containsKey(parameterId))
                throw new IllegalArgumentException("Factory parameter `" + parameterId + "` is not declared");
        }

        if (factory.inputKind() == StudyInputKind.PRICE_FIELD) {
            if (factory.inputParameter().isBlank())
                throw new IllegalArgumentException("PRICE_FIELD studies must declare an inputParameter");
            if (!parameters.containsKey(factory.inputParameter()))
                throw new IllegalArgumentException("Input parameter `" + factory.inputParameter() + "` is not declared");
        }
    }

    private static void validatePlots(List<StudyPlotDescriptor> plots,
                                      SequencedMap<String, StudyParameterDescriptor> parameters,
                                      SequencedMap<String, StudyOutputDescriptor> outputs) {
        Set<String> plotIds = new LinkedHashSet<>();
        for (StudyPlotDescriptor plot : plots) {
            if (!plotIds.add(plot.id()))
                throw new IllegalArgumentException("Duplicate plot id: " + plot.id());

            requireKnownOutput(plot.outputId(), outputs, "plot output");
            requireKnownOutput(plot.secondaryOutputId(), outputs, "secondary plot output");
            requireKnownParameter(plot.colorParameter(), parameters, "color parameter");
            requireKnownParameter(plot.secondaryColorParameter(), parameters, "secondary color parameter");
            requireKnownParameter(plot.strokeParameter(), parameters, "stroke parameter");
            requireKnownParameter(plot.visibilityParameterId(), parameters, "visibility parameter");
            requireBooleanVisibilityParameter(plot.visibilityParameterId(), parameters);
            requireKnownParameter(plot.panelParameterId(), parameters, "panel parameter");
            requireIntegerPanelParameter(plot.panelParameterId(), parameters);
        }
    }

    private static void addPlotVisualParameters(
            List<StudyPlotDescriptor> plots,
            LinkedHashMap<String, StudyParameterDescriptor> parameters) {
        for (StudyPlotDescriptor plot : plots) {
            addVisibilityParameter(
                    parameters, plot.visibilityParameterId(), plot.label(), plot.visibleByDefault());
            addPanelParameter(parameters, plot.panelParameterId(), plot.label());
        }
    }

    private static void addVisibilityParameter(
            LinkedHashMap<String, StudyParameterDescriptor> parameters,
            String parameterId,
            String plotLabel,
            boolean visibleByDefault) {
        parameters.putIfAbsent(parameterId, new StudyParameterDescriptor(
                parameterId,
                plotLabel + " Visibility",
                "Controls whether the " + plotLabel + " plot is displayed",
                StudyParameterScope.VISUAL,
                StudyParameterType.BOOLEAN,
                Boolean.class,
                Void.class,
                Boolean.toString(visibleByDefault),
                Integer.MAX_VALUE,
                StudyStereotype.NONE));
    }

    private static void addPanelParameter(
            LinkedHashMap<String, StudyParameterDescriptor> parameters,
            String parameterId,
            String plotLabel) {
        parameters.putIfAbsent(parameterId, new StudyParameterDescriptor(
                parameterId,
                plotLabel + " Panel",
                "Selects the chart panel used by the " + plotLabel + " plot",
                StudyParameterScope.VISUAL,
                StudyParameterType.INTEGER,
                Integer.class,
                Void.class,
                Integer.toString(StudyPlotDescriptor.INHERITED_PANEL_ID),
                Integer.MAX_VALUE,
                StudyStereotype.NONE));
    }

    private static void requireBooleanVisibilityParameter(
            String parameterId,
            SequencedMap<String, StudyParameterDescriptor> parameters) {
        StudyParameterDescriptor parameter = parameters.get(parameterId);
        if (parameter != null
                && parameter.effectiveValueType() != Boolean.class
                && parameter.effectiveValueType() != boolean.class)
            throw new IllegalArgumentException(
                    "Visibility parameter must be boolean: " + parameterId);
    }

    private static void requireIntegerPanelParameter(
            String parameterId,
            SequencedMap<String, StudyParameterDescriptor> parameters) {
        StudyParameterDescriptor parameter = parameters.get(parameterId);
        if (parameter != null
                && parameter.effectiveValueType() != Integer.class
                && parameter.effectiveValueType() != int.class)
            throw new IllegalArgumentException("Panel parameter must be integer: " + parameterId);
    }

    private static void requireKnownOutput(String outputId,
                                           SequencedMap<String, StudyOutputDescriptor> outputs,
                                           String role) {
        if (!outputId.isBlank() && !outputs.containsKey(outputId))
            throw new IllegalArgumentException("Unknown " + role + ": " + outputId);
    }

    private static void requireKnownParameter(String parameterId,
                                              SequencedMap<String, StudyParameterDescriptor> parameters,
                                              String role) {
        if (!parameterId.isBlank() && !parameters.containsKey(parameterId))
            throw new IllegalArgumentException("Unknown " + role + ": " + parameterId);
    }
}
