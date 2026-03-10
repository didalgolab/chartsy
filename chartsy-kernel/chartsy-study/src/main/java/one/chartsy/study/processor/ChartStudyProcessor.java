package one.chartsy.study.processor;

import one.chartsy.study.BarPlotSpec;
import one.chartsy.study.ChartStudy;
import one.chartsy.study.FillPlotSpec;
import one.chartsy.study.HistogramPlotSpec;
import one.chartsy.study.HorizontalLinePlotSpec;
import one.chartsy.study.InsideFillPlotSpec;
import one.chartsy.study.LinePlotSpec;
import one.chartsy.study.ShapePlotSpec;
import one.chartsy.study.StudyAxis;
import one.chartsy.study.StudyAxisDescriptor;
import one.chartsy.study.StudyDescriptor;
import one.chartsy.study.StudyDescriptorProvider;
import one.chartsy.study.StudyFactory;
import one.chartsy.study.StudyFactoryDescriptor;
import one.chartsy.study.StudyFactoryTarget;
import one.chartsy.study.StudyMemberTarget;
import one.chartsy.study.StudyOutputDescriptor;
import one.chartsy.study.StudyOutput;
import one.chartsy.study.StudyParameterDescriptor;
import one.chartsy.study.StudyParameter;
import one.chartsy.study.StudyParameterType;
import one.chartsy.study.StudyPlotType;
import one.chartsy.study.StudyPresentationBuilder;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@SupportedAnnotationTypes("one.chartsy.study.ChartStudy")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class ChartStudyProcessor extends AbstractProcessor {
    private static final String PROVIDER_PACKAGE = "one.chartsy.study.generated";
    private static final String PROVIDER_SERVICE = "META-INF/services/one.chartsy.study.StudyDescriptorProvider";

    private final SequencedMap<String, TypeElement> collectedStudies = new LinkedHashMap<>();
    private Messager messager;
    private Filer filer;
    private boolean generated;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(ChartStudy.class)) {
            if (element instanceof TypeElement type)
                collectedStudies.put(type.getQualifiedName().toString(), type);
        }

        if (generated || !roundEnv.processingOver() || collectedStudies.isEmpty())
            return false;

        List<StudyModel> studies = collectedStudies.values().stream()
                .sorted(Comparator.comparing(type -> type.getQualifiedName().toString()))
                .map(this::toStudyModel)
                .filter(Objects::nonNull)
                .toList();
        if (studies.isEmpty())
            return false;

        try {
            studies = mergeWithExistingStudies(studies);
            writeProvider(studies);
            generated = true;
        } catch (IOException ex) {
            error(null, "Failed to write generated study provider: %s", ex.getMessage());
        }
        return false;
    }

    private List<StudyModel> mergeWithExistingStudies(List<StudyModel> currentStudies) throws IOException {
        var mergedStudies = new LinkedHashMap<String, StudyModel>();
        for (StudyModel study : readExistingStudies()) {
            mergedStudies.put(study.id(), study);
        }

        Set<String> currentDefinitionTypes = currentStudies.stream()
                .map(StudyModel::definitionType)
                .collect(Collectors.toSet());
        mergedStudies.entrySet().removeIf(entry -> currentDefinitionTypes.contains(entry.getValue().definitionType()));

        for (StudyModel study : currentStudies) {
            mergedStudies.put(study.id(), study);
        }

        return mergedStudies.values().stream()
                .sorted(Comparator.comparing(StudyModel::definitionType))
                .toList();
    }

    private List<StudyModel> readExistingStudies() throws IOException {
        Path serviceFile = existingServiceFile();
        if (serviceFile == null || !Files.exists(serviceFile))
            return List.of();

        Path classOutputRoot = classOutputRoot(serviceFile);
        try (var classLoader = new URLClassLoader(
                new java.net.URL[] { classOutputRoot.toUri().toURL() },
                ChartStudyProcessor.class.getClassLoader())) {
            var models = new LinkedHashMap<String, StudyModel>();
            for (String providerClassName : Files.readAllLines(serviceFile)) {
                String trimmedName = providerClassName.strip();
                if (trimmedName.isEmpty())
                    continue;
                for (StudyDescriptor descriptor : loadExistingDescriptors(trimmedName, classLoader)) {
                    models.put(descriptor.id(), studyModel(descriptor));
                }
            }
            return List.copyOf(models.values());
        } catch (ReflectiveOperationException | SecurityException ex) {
            warning("Failed to inspect existing generated study providers: %s", ex.getMessage());
            return List.of();
        }
    }

    private List<StudyDescriptor> loadExistingDescriptors(String providerClassName, ClassLoader classLoader)
            throws ReflectiveOperationException {
        try {
            Class<?> providerClass = Class.forName(providerClassName, false, classLoader);
            if (providerClassName.startsWith(PROVIDER_PACKAGE + ".GeneratedStudyDescriptorProvider_"))
                return loadGeneratedDescriptors(providerClass);

            if (!StudyDescriptorProvider.class.isAssignableFrom(providerClass))
                return List.of();

            StudyDescriptorProvider provider = (StudyDescriptorProvider) providerClass.getDeclaredConstructor().newInstance();
            return List.copyOf(provider.getStudyDescriptors());
        } catch (ClassNotFoundException | LinkageError ex) {
            warning("Skipping stale study provider %s: %s", providerClassName, ex.getMessage());
            return List.of();
        }
    }

    private List<StudyDescriptor> loadGeneratedDescriptors(Class<?> providerClass) throws ReflectiveOperationException {
        List<StudyDescriptor> descriptors = new ArrayList<>();
        for (Method method : Arrays.stream(providerClass.getDeclaredMethods())
                .filter(method -> method.getParameterCount() == 0)
                .filter(method -> method.getName().startsWith("descriptor"))
                .filter(method -> StudyDescriptor.class.equals(method.getReturnType()))
                .sorted(Comparator.comparing(Method::getName))
                .toList()) {
            method.setAccessible(true);
            try {
                descriptors.add((StudyDescriptor) method.invoke(null));
            } catch (InvocationTargetException ex) {
                Throwable cause = ex.getCause();
                warning("Skipping stale generated study descriptor %s#%s: %s",
                        providerClass.getName(),
                        method.getName(),
                        cause == null ? ex.getMessage() : cause.getMessage());
            } catch (LinkageError ex) {
                warning("Skipping stale generated study descriptor %s#%s: %s",
                        providerClass.getName(),
                        method.getName(),
                        ex.getMessage());
            }
        }
        return descriptors;
    }

    private Path existingServiceFile() throws IOException {
        var resource = filer.getResource(StandardLocation.CLASS_OUTPUT, "", PROVIDER_SERVICE);
        if (!"file".equalsIgnoreCase(resource.toUri().getScheme()))
            return null;
        return Path.of(resource.toUri());
    }

    private Path classOutputRoot(Path serviceFile) {
        return serviceFile.getParent().getParent().getParent();
    }

    private StudyModel studyModel(StudyDescriptor descriptor) {
        List<ParameterModel> parameters = new ArrayList<>();
        int parameterDeclarationOrder = 0;
        for (StudyParameterDescriptor parameter : descriptor.parameters().values()) {
            parameters.add(new ParameterModel(
                    parameter.id(),
                    parameter.name(),
                    parameter.description(),
                    parameter.scope().name(),
                    parameter.type().name(),
                    classLiteral(parameter.valueType()),
                    classLiteral(parameter.enumType()),
                    parameter.defaultValue(),
                    parameter.order(),
                    parameterDeclarationOrder++,
                    parameter.stereotype().name()
            ));
        }

        List<OutputModel> outputs = new ArrayList<>();
        for (StudyOutputDescriptor output : descriptor.outputs().values()) {
            outputs.add(new OutputModel(
                    output.id(),
                    output.name(),
                    output.description(),
                    output.order(),
                    output.target().name(),
                    output.memberName(),
                    classLiteral(output.valueType())
            ));
        }

        StudyAxisDescriptor axis = descriptor.axis();
        AxisModel axisModel = new AxisModel(
                doubleLiteral(axis.min()),
                doubleLiteral(axis.max()),
                axis.logarithmic(),
                axis.includeInRange(),
                Arrays.stream(axis.steps()).mapToObj(ChartStudyProcessor::doubleLiteral).toList()
        );

        List<PlotModel> plots = descriptor.plots().stream()
                .map(plot -> new PlotModel(
                        plot.id(),
                        plot.label(),
                        plot.order(),
                        plot.type().name(),
                        plot.outputId(),
                        plot.secondaryOutputId(),
                        doubleLiteral(plot.value1()),
                        doubleLiteral(plot.value2()),
                        plot.upper(),
                        plot.colorParameter(),
                        plot.secondaryColorParameter(),
                        plot.strokeParameter(),
                        plot.visibleParameter(),
                        plot.marker().name()
                ))
                .sorted(Comparator.comparingInt(PlotModel::order).thenComparing(PlotModel::label))
                .toList();

        StudyFactoryDescriptor factory = descriptor.factory();
        return new StudyModel(
                descriptor.id(),
                descriptor.name(),
                descriptor.label(),
                descriptor.category(),
                descriptor.kind().name(),
                descriptor.placement().name(),
                descriptor.definitionType().getCanonicalName(),
                descriptor.implementationType().getCanonicalName(),
                new FactoryModel(
                        factory.inputKind().name(),
                        factory.inputParameter(),
                        factory.target().name(),
                        factory.memberName(),
                        factory.parameterIds()
                ),
                parameters.stream()
                        .sorted(Comparator.comparingInt(ParameterModel::order).thenComparingInt(ParameterModel::declarationOrder))
                        .toList(),
                outputs.stream()
                        .sorted(Comparator.comparingInt(OutputModel::order).thenComparing(OutputModel::id))
                        .toList(),
                axisModel,
                plots,
                descriptor.builderType().getCanonicalName()
        );
    }

    private static String classLiteral(Class<?> type) {
        if (type == null)
            return "java.lang.Void.class";
        return normalizeClassLiteral(type.getCanonicalName());
    }

    private StudyModel toStudyModel(TypeElement definitionType) {
        ChartStudy chartStudy = definitionType.getAnnotation(ChartStudy.class);
        if (chartStudy == null)
            return null;

        ExecutableElement factory = findFactory(definitionType);
        if (factory == null)
            return null;

        String builderType = typeName(() -> chartStudy.builder());
        String implementationType = typeName(() -> chartStudy.implementation());
        if (Void.class.getCanonicalName().equals(implementationType))
            implementationType = definitionType.getQualifiedName().toString();

        String id = chartStudy.id().isBlank() ? definitionType.getQualifiedName().toString() : chartStudy.id();
        StudyFactory factoryAnnotation = factory.getAnnotation(StudyFactory.class);
        StudyFactoryTarget factoryTarget = factory.getKind() == ElementKind.CONSTRUCTOR
                ? StudyFactoryTarget.CONSTRUCTOR
                : StudyFactoryTarget.STATIC_METHOD;

        List<ParameterModel> parameters = new ArrayList<>();
        List<String> factoryParameterIds = new ArrayList<>();
        int declarationOrder = 0;
        for (StudyParameter parameter : definitionType.getAnnotationsByType(StudyParameter.class)) {
            parameters.add(parameterModel(parameter, null, declarationOrder++));
        }
        for (VariableElement parameterElement : factory.getParameters()) {
            List<StudyParameter> declaredParameters = List.of(parameterElement.getAnnotationsByType(StudyParameter.class));
            if (declaredParameters.isEmpty()) {
                error(parameterElement, "Factory parameter %s on %s must declare exactly one @StudyParameter", parameterElement.getSimpleName(), definitionType.getQualifiedName());
                return null;
            }
            if (declaredParameters.size() > 1) {
                error(parameterElement, "Factory parameter %s on %s declares multiple @StudyParameter annotations", parameterElement.getSimpleName(), definitionType.getQualifiedName());
                return null;
            }

            StudyParameter parameter = declaredParameters.getFirst();
            factoryParameterIds.add(parameter.id());
            parameters.add(parameterModel(parameter, parameterElement.asType(), declarationOrder++));
        }
        parameters = parameters.stream()
                .sorted(Comparator.comparingInt(ParameterModel::order).thenComparingInt(ParameterModel::declarationOrder))
                .toList();

        List<OutputModel> outputs = definitionType.getEnclosedElements().stream()
                .map(this::outputModel)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(OutputModel::order).thenComparing(OutputModel::id))
                .toList();
        if (outputs.isEmpty() && StudyPresentationBuilder.class.getCanonicalName().equals(builderType)) {
            error(definitionType, "Study %s must declare at least one @StudyOutput or use a custom StudyPresentationBuilder", definitionType.getQualifiedName());
            return null;
        }

        StudyAxis axis = definitionType.getAnnotation(StudyAxis.class);
        AxisModel axisModel = axis == null
                ? new AxisModel("Double.NaN", "Double.NaN", false, true, List.of())
                : new AxisModel(
                        doubleLiteral(axis.min()),
                        doubleLiteral(axis.max()),
                        axis.logarithmic(),
                        axis.includeInRange(),
                        Arrays.stream(axis.steps()).mapToObj(ChartStudyProcessor::doubleLiteral).toList()
                );

        List<PlotModel> plots = new ArrayList<>();
        addPlots(plots, List.of(definitionType.getAnnotationsByType(LinePlotSpec.class)), StudyPlotType.LINE);
        addPlots(plots, List.of(definitionType.getAnnotationsByType(HistogramPlotSpec.class)), StudyPlotType.HISTOGRAM);
        addPlots(plots, List.of(definitionType.getAnnotationsByType(BarPlotSpec.class)), StudyPlotType.BAR);
        addPlots(plots, List.of(definitionType.getAnnotationsByType(HorizontalLinePlotSpec.class)), StudyPlotType.HORIZONTAL_LINE);
        addPlots(plots, List.of(definitionType.getAnnotationsByType(FillPlotSpec.class)), StudyPlotType.FILL);
        addPlots(plots, List.of(definitionType.getAnnotationsByType(InsideFillPlotSpec.class)), StudyPlotType.INSIDE_FILL);
        addPlots(plots, List.of(definitionType.getAnnotationsByType(ShapePlotSpec.class)), StudyPlotType.SHAPE);
        plots.sort(Comparator.comparingInt(PlotModel::order).thenComparing(PlotModel::label));

        return new StudyModel(
                id,
                chartStudy.name(),
                chartStudy.label(),
                chartStudy.category(),
                chartStudy.kind().name(),
                chartStudy.placement().name(),
                definitionType.getQualifiedName().toString(),
                implementationType,
                new FactoryModel(factoryAnnotation.input().name(), factoryAnnotation.inputParameter(), factoryTarget.name(), factory.getSimpleName().toString(), factoryParameterIds),
                parameters,
                outputs,
                axisModel,
                plots,
                builderType
        );
    }

    private void addPlots(List<PlotModel> target, List<?> annotations, StudyPlotType type) {
        for (Object annotation : annotations) {
            switch (type) {
                case LINE -> {
                    LinePlotSpec spec = (LinePlotSpec) annotation;
                    target.add(new PlotModel(spec.id(), spec.label(), spec.order(), type.name(), spec.output(), "",
                            "Double.NaN", "Double.NaN", true, spec.colorParameter(), "", spec.strokeParameter(),
                            spec.visibleParameter(), "NONE"));
                }
                case HISTOGRAM -> {
                    HistogramPlotSpec spec = (HistogramPlotSpec) annotation;
                    target.add(new PlotModel(spec.id(), spec.label(), spec.order(), type.name(), spec.output(), "",
                            "Double.NaN", "Double.NaN", true, spec.positiveColorParameter(), spec.negativeColorParameter(), "",
                            spec.visibleParameter(), "NONE"));
                }
                case BAR -> {
                    BarPlotSpec spec = (BarPlotSpec) annotation;
                    target.add(new PlotModel(spec.id(), spec.label(), spec.order(), type.name(), spec.output(), "",
                            "Double.NaN", "Double.NaN", true, spec.colorParameter(), "", "", spec.visibleParameter(), "NONE"));
                }
                case HORIZONTAL_LINE -> {
                    HorizontalLinePlotSpec spec = (HorizontalLinePlotSpec) annotation;
                    target.add(new PlotModel(spec.id(), spec.label(), spec.order(), type.name(), "", "",
                            doubleLiteral(spec.value()), "Double.NaN", true, spec.colorParameter(), "", spec.strokeParameter(),
                            spec.visibleParameter(), "NONE"));
                }
                case FILL -> {
                    FillPlotSpec spec = (FillPlotSpec) annotation;
                    target.add(new PlotModel(spec.id(), spec.label(), spec.order(), type.name(), spec.output(), "",
                            doubleLiteral(spec.from()), doubleLiteral(spec.to()), spec.upper(), spec.colorParameter(), "", "",
                            spec.visibleParameter(), "NONE"));
                }
                case INSIDE_FILL -> {
                    InsideFillPlotSpec spec = (InsideFillPlotSpec) annotation;
                    target.add(new PlotModel(spec.id(), spec.label(), spec.order(), type.name(), spec.upperOutput(), spec.lowerOutput(),
                            "Double.NaN", "Double.NaN", true, spec.colorParameter(), "", "", spec.visibleParameter(), "NONE"));
                }
                case SHAPE -> {
                    ShapePlotSpec spec = (ShapePlotSpec) annotation;
                    target.add(new PlotModel(spec.id(), spec.label(), spec.order(), type.name(), spec.output(), "",
                            "Double.NaN", "Double.NaN", true, spec.colorParameter(), "", "", spec.visibleParameter(), spec.marker().name()));
                }
            }
        }
    }

    private ParameterModel parameterModel(StudyParameter parameter, TypeMirror declaredType, int declarationOrder) {
        String valueType = typeName(() -> parameter.valueType());
        String enumType = typeName(() -> parameter.enumType());
        String parameterType = parameter.type() == StudyParameterType.AUTO
                ? inferParameterType(declaredType, valueType, enumType)
                : parameter.type().name();
        String effectiveValueType = normalizeValueType(declaredType, valueType, parameterType, enumType);

        return new ParameterModel(
                parameter.id(),
                parameter.name(),
                parameter.description(),
                parameter.scope().name(),
                parameterType,
                effectiveValueType,
                normalizeClassLiteral(enumType),
                parameter.defaultValue(),
                parameter.order(),
                declarationOrder,
                parameter.stereotype().name()
        );
    }

    private OutputModel outputModel(Element element) {
        StudyOutput annotation = element.getAnnotation(StudyOutput.class);
        if (annotation == null)
            return null;

        StudyMemberTarget target = switch (element.getKind()) {
            case FIELD -> StudyMemberTarget.FIELD;
            case METHOD -> StudyMemberTarget.METHOD;
            default -> null;
        };
        if (target == null) {
            error(element, "@StudyOutput can only be placed on fields or methods");
            return null;
        }

        String valueType = switch (element) {
            case VariableElement field -> normalizeClassLiteral(field.asType().toString());
            case ExecutableElement method -> normalizeClassLiteral(method.getReturnType().toString());
            default -> "java.lang.Double.class";
        };

        return new OutputModel(
                annotation.id(),
                annotation.name().isBlank() ? annotation.id() : annotation.name(),
                annotation.description(),
                annotation.order(),
                target.name(),
                element.getSimpleName().toString(),
                valueType
        );
    }

    private ExecutableElement findFactory(TypeElement type) {
        List<ExecutableElement> factories = type.getEnclosedElements().stream()
                .filter(element -> element instanceof ExecutableElement)
                .map(ExecutableElement.class::cast)
                .filter(element -> element.getAnnotation(StudyFactory.class) != null)
                .toList();
        if (factories.isEmpty()) {
            error(type, "Study %s must declare exactly one @StudyFactory constructor or static method", type.getQualifiedName());
            return null;
        }
        if (factories.size() > 1) {
            error(type, "Study %s declares multiple @StudyFactory members", type.getQualifiedName());
            return null;
        }

        ExecutableElement factory = factories.get(0);
        if (factory.getKind() == ElementKind.METHOD && !factory.getModifiers().contains(Modifier.STATIC)) {
            error(factory, "@StudyFactory methods must be static");
            return null;
        }
        return factory;
    }

    private String inferParameterType(TypeMirror declaredType, String valueType, String enumType) {
        String normalizedEnumType = normalizeClassLiteral(enumType);
        if (!"java.lang.Void.class".equals(normalizedEnumType))
            return "ENUM";

        String normalizedType = normalizeClassLiteral(valueType);
        if (!"java.lang.Void.class".equals(normalizedType))
            return inferParameterTypeFromName(normalizedType.replace(".class", ""));
        if (declaredType == null)
            return "STRING";
        return inferParameterTypeFromMirror(declaredType);
    }

    private String inferParameterTypeFromMirror(TypeMirror type) {
        if (type.getKind() == TypeKind.BOOLEAN)
            return "BOOLEAN";
        if (type.getKind() == TypeKind.INT || type.getKind() == TypeKind.LONG || type.getKind() == TypeKind.SHORT || type.getKind() == TypeKind.BYTE)
            return "INTEGER";
        if (type.getKind() == TypeKind.DOUBLE || type.getKind() == TypeKind.FLOAT)
            return "DOUBLE";
        if (type.getKind() == TypeKind.DECLARED) {
            Element element = processingEnv.getTypeUtils().asElement(type);
            if (element instanceof TypeElement declared && declared.getKind() == ElementKind.ENUM)
                return "ENUM";
        }
        return inferParameterTypeFromName(type.toString());
    }

    private String inferParameterTypeFromName(String typeName) {
        return switch (typeName) {
            case "boolean", "java.lang.Boolean" -> "BOOLEAN";
            case "byte", "short", "int", "long", "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long" -> "INTEGER";
            case "float", "double", "java.lang.Float", "java.lang.Double" -> "DOUBLE";
            case "java.lang.String" -> "STRING";
            default -> typeName.endsWith("Color") ? "COLOR" : "STRING";
        };
    }

    private String normalizeValueType(TypeMirror declaredType, String valueType, String parameterType, String enumType) {
        String normalizedType = normalizeClassLiteral(valueType);
        if (!"java.lang.Void.class".equals(normalizedType))
            return normalizedType;
        if ("ENUM".equals(parameterType))
            return normalizeClassLiteral(enumType);
        if (declaredType != null)
            return normalizeClassLiteral(declaredType.toString());
        return switch (parameterType) {
            case "INTEGER" -> "java.lang.Integer.class";
            case "DOUBLE" -> "java.lang.Double.class";
            case "BOOLEAN" -> "java.lang.Boolean.class";
            case "COLOR" -> "one.chartsy.study.StudyColor.class";
            default -> "java.lang.String.class";
        };
    }

    private void writeProvider(List<StudyModel> studies) throws IOException {
        String hashSource = studies.stream().map(StudyModel::id).collect(Collectors.joining("|"));
        String className = "GeneratedStudyDescriptorProvider_" + Integer.toUnsignedString(hashSource.hashCode());
        String qualifiedName = PROVIDER_PACKAGE + '.' + className;

        JavaFileObject sourceFile = filer.createSourceFile(qualifiedName, collectedStudies.values().toArray(Element[]::new));
        try (Writer writer = sourceFile.openWriter()) {
            writer.write(renderProviderSource(PROVIDER_PACKAGE, className, studies));
        }

        FileObject serviceFile = filer.createResource(StandardLocation.CLASS_OUTPUT, "", PROVIDER_SERVICE, collectedStudies.values().toArray(Element[]::new));
        try (Writer writer = serviceFile.openWriter()) {
            writer.write(qualifiedName);
            writer.write(System.lineSeparator());
        }
    }

    private String renderProviderSource(String packageName, String className, List<StudyModel> studies) {
        StringBuilder out = new StringBuilder(32_768);
        out.append("package ").append(packageName).append(";\n\n");
        out.append("import one.chartsy.study.*;\n\n");
        out.append("import java.util.LinkedHashMap;\n");
        out.append("import java.util.List;\n\n");
        out.append("public final class ").append(className).append(" implements StudyDescriptorProvider {\n");
        out.append("    @Override\n");
        out.append("    public List<StudyDescriptor> getStudyDescriptors() {\n");
        out.append("        return List.of(");
        for (int i = 0; i < studies.size(); i++) {
            if (i > 0)
                out.append(", ");
            out.append("descriptor").append(i).append("()");
        }
        out.append(");\n");
        out.append("    }\n\n");

        for (int i = 0; i < studies.size(); i++) {
            StudyModel study = studies.get(i);
            out.append("    private static StudyDescriptor descriptor").append(i).append("() {\n");
            out.append("        var parameters = new LinkedHashMap<String, StudyParameterDescriptor>();\n");
            for (ParameterModel parameter : study.parameters()) {
                out.append("        parameters.put(")
                        .append(stringLiteral(parameter.id())).append(", new StudyParameterDescriptor(")
                        .append(stringLiteral(parameter.id())).append(", ")
                        .append(stringLiteral(parameter.name())).append(", ")
                        .append(stringLiteral(parameter.description())).append(", ")
                        .append("StudyParameterScope.").append(parameter.scope()).append(", ")
                        .append("StudyParameterType.").append(parameter.type()).append(", ")
                        .append(parameter.valueType()).append(", ")
                        .append(parameter.enumType()).append(", ")
                        .append(stringLiteral(parameter.defaultValue())).append(", ")
                        .append(parameter.order()).append(", ")
                        .append("StudyStereotype.").append(parameter.stereotype()).append("));\n");
            }
            out.append("        var outputs = new LinkedHashMap<String, StudyOutputDescriptor>();\n");
            for (OutputModel output : study.outputs()) {
                out.append("        outputs.put(")
                        .append(stringLiteral(output.id())).append(", new StudyOutputDescriptor(")
                        .append(stringLiteral(output.id())).append(", ")
                        .append(stringLiteral(output.name())).append(", ")
                        .append(stringLiteral(output.description())).append(", ")
                        .append(output.order()).append(", ")
                        .append("StudyMemberTarget.").append(output.target()).append(", ")
                        .append(stringLiteral(output.memberName())).append(", ")
                        .append(output.valueType()).append("));\n");
            }
            out.append("        var plots = new java.util.ArrayList<StudyPlotDescriptor>();\n");
            for (PlotModel plot : study.plots()) {
                out.append("        plots.add(new StudyPlotDescriptor(")
                        .append(stringLiteral(plot.id())).append(", ")
                        .append(stringLiteral(plot.label())).append(", ")
                        .append(plot.order()).append(", ")
                        .append("StudyPlotType.").append(plot.type()).append(", ")
                        .append(stringLiteral(plot.outputId())).append(", ")
                        .append(stringLiteral(plot.secondaryOutputId())).append(", ")
                        .append(plot.value1()).append(", ")
                        .append(plot.value2()).append(", ")
                        .append(plot.upper()).append(", ")
                        .append(stringLiteral(plot.colorParameter())).append(", ")
                        .append(stringLiteral(plot.secondaryColorParameter())).append(", ")
                        .append(stringLiteral(plot.strokeParameter())).append(", ")
                        .append(stringLiteral(plot.visibleParameter())).append(", ")
                        .append("StudyMarkerType.").append(plot.marker()).append("));\n");
            }
            out.append("        var axis = new StudyAxisDescriptor(")
                    .append(study.axis().min()).append(", ")
                    .append(study.axis().max()).append(", ")
                    .append(study.axis().logarithmic()).append(", ")
                    .append(study.axis().includeInRange()).append(", new double[] {");
            for (int stepIndex = 0; stepIndex < study.axis().steps().size(); stepIndex++) {
                if (stepIndex > 0)
                    out.append(", ");
                out.append(study.axis().steps().get(stepIndex));
            }
            out.append("});\n");
            out.append("        return new StudyDescriptor(")
                    .append(stringLiteral(study.id())).append(", ")
                    .append(stringLiteral(study.name())).append(", ")
                    .append(stringLiteral(study.label())).append(", ")
                    .append(stringLiteral(study.category())).append(", ")
                    .append("StudyKind.").append(study.kind()).append(", ")
                    .append("StudyPlacement.").append(study.placement()).append(", ")
                    .append(study.definitionType()).append(".class, ")
                    .append(study.implementationType()).append(".class, ")
                    .append("new StudyFactoryDescriptor(StudyInputKind.").append(study.factory().inputKind())
                    .append(", ").append(stringLiteral(study.factory().inputParameter()))
                    .append(", StudyFactoryTarget.").append(study.factory().target())
                    .append(", ").append(stringLiteral(study.factory().memberName()))
                    .append(", java.util.List.of(");
            for (int parameterIndex = 0; parameterIndex < study.factory().parameterIds().size(); parameterIndex++) {
                if (parameterIndex > 0)
                    out.append(", ");
                out.append(stringLiteral(study.factory().parameterIds().get(parameterIndex)));
            }
            out.append(")), ")
                    .append("parameters, outputs, axis, plots, ")
                    .append(study.builderType()).append(".class);\n");
            out.append("    }\n\n");
        }
        out.append("}\n");
        return out.toString();
    }

    private static String stringLiteral(String text) {
        if (text == null)
            return "null";
        return '"' + text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + '"';
    }

    private static String normalizeClassLiteral(String typeName) {
        if (typeName == null || typeName.isBlank() || "<error>".equals(typeName))
            return "java.lang.Void.class";
        return switch (typeName) {
            case "void" -> "void.class";
            case "boolean" -> "boolean.class";
            case "byte" -> "byte.class";
            case "short" -> "short.class";
            case "int" -> "int.class";
            case "long" -> "long.class";
            case "float" -> "float.class";
            case "double" -> "double.class";
            case "char" -> "char.class";
            default -> typeName.endsWith(".class") ? typeName : typeName + ".class";
        };
    }

    private static String doubleLiteral(double value) {
        if (Double.isNaN(value))
            return "Double.NaN";
        if (Double.isInfinite(value))
            return value > 0 ? "Double.POSITIVE_INFINITY" : "Double.NEGATIVE_INFINITY";
        return Double.toString(value);
    }

    private String typeName(Supplier<Class<?>> typeSupplier) {
        try {
            Class<?> type = typeSupplier.get();
            return type == null ? Void.class.getCanonicalName() : type.getCanonicalName();
        } catch (MirroredTypeException ex) {
            TypeMirror mirror = ex.getTypeMirror();
            return mirror == null ? Void.class.getCanonicalName() : mirror.toString();
        }
    }

    private void error(Element element, String message, Object... args) {
        messager.printMessage(Diagnostic.Kind.ERROR, String.format(message, args), element);
    }

    private void warning(String message, Object... args) {
        messager.printMessage(Diagnostic.Kind.WARNING, String.format(message, args));
    }

    private record StudyModel(
            String id,
            String name,
            String label,
            String category,
            String kind,
            String placement,
            String definitionType,
            String implementationType,
            FactoryModel factory,
            List<ParameterModel> parameters,
            List<OutputModel> outputs,
            AxisModel axis,
            List<PlotModel> plots,
            String builderType
    ) {
    }

    private record FactoryModel(String inputKind, String inputParameter, String target, String memberName, List<String> parameterIds) {
    }

    private record ParameterModel(
            String id,
            String name,
            String description,
            String scope,
            String type,
            String valueType,
            String enumType,
            String defaultValue,
            int order,
            int declarationOrder,
            String stereotype
    ) {
    }

    private record OutputModel(
            String id,
            String name,
            String description,
            int order,
            String target,
            String memberName,
            String valueType
    ) {
    }

    private record AxisModel(
            String min,
            String max,
            boolean logarithmic,
            boolean includeInRange,
            List<String> steps
    ) {
    }

    private record PlotModel(
            String id,
            String label,
            int order,
            String type,
            String outputId,
            String secondaryOutputId,
            String value1,
            String value2,
            boolean upper,
            String colorParameter,
            String secondaryColorParameter,
            String strokeParameter,
            String visibleParameter,
            String marker
    ) {
    }
}
