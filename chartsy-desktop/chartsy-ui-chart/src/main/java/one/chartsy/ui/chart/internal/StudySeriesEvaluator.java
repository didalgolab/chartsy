package one.chartsy.ui.chart.internal;

import one.chartsy.Candle;
import one.chartsy.CandleField;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DoubleSeries;
import one.chartsy.financial.ValueIndicator;
import one.chartsy.study.StudyDescriptor;
import one.chartsy.study.StudyFactoryDescriptor;
import one.chartsy.study.StudyFactoryTarget;
import one.chartsy.study.StudyInputKind;
import one.chartsy.study.StudyMemberTarget;
import one.chartsy.study.StudyOutputDescriptor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

public final class StudySeriesEvaluator {
    private static final Map<String, CompiledStudy> CACHE = new ConcurrentHashMap<>();

    private StudySeriesEvaluator() {
    }

    public static StudyEvaluation evaluate(StudyDescriptor descriptor, CandleSeries dataset, Map<String, ?> uiParameters) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(uiParameters, "uiParameters");

        CompiledStudy compiled = CACHE.computeIfAbsent(descriptor.id(), ignored -> compile(descriptor));
        Object indicator = compiled.factory().create(uiParameters);
        Object input = resolveInput(descriptor, dataset, uiParameters);
        int length = inputLength(input);
        double[][] values = new double[compiled.outputs().size()][];

        for (int index = length - 1; index >= 0; index--) {
            accept(input, index, indicator, descriptor.factory().inputKind());
            boolean ready = !(indicator instanceof ValueIndicator valueIndicator) || valueIndicator.isReady();

            for (int out = 0; out < compiled.outputs().size(); out++) {
                double outputValue = ready ? compiled.outputs().get(out).extract(indicator) : Double.NaN;
                if (values[out] == null && !Double.isNaN(outputValue)) {
                    values[out] = new double[index + 1];
                }
                if (values[out] != null) {
                    values[out][index] = outputValue;
                }
            }
        }

        var outputs = new LinkedHashMap<String, DoubleSeries>();
        for (int out = 0; out < compiled.outputs().size(); out++) {
            OutputAccessor accessor = compiled.outputs().get(out);
            DoubleSeries series = values[out] == null
                    ? DoubleSeries.empty(dataset.getTimeline())
                    : DoubleSeries.of(values[out], dataset.getTimeline());
            outputs.put(accessor.id(), series);
        }
        return new StudyEvaluation(Collections.unmodifiableSequencedMap(outputs));
    }

    private static CompiledStudy compile(StudyDescriptor descriptor) {
        return new CompiledStudy(
                compileFactory(descriptor),
                descriptor.outputs().values().stream().map(output -> compileOutput(descriptor, output)).toList()
        );
    }

    private static FactoryAccessor compileFactory(StudyDescriptor descriptor) {
        StudyFactoryDescriptor factory = descriptor.factory();
        List<Class<?>> parameterTypes = factory.parameterIds().stream()
                .map(descriptor::parameter)
                .filter(Objects::nonNull)
                .map(parameter -> switch (parameter.type()) {
                    case ENUM -> parameter.enumType() != Void.class ? parameter.enumType() : parameter.effectiveValueType();
                    default -> parameter.effectiveValueType();
                })
                .toList();

        MethodHandle handle = switch (factory.target()) {
            case STATIC_METHOD -> staticMethodHandle(resolveFactoryMethod(descriptor.definitionType(), factory, parameterTypes));
            case CONSTRUCTOR -> constructorHandle(resolveConstructor(descriptor.definitionType(), factory, parameterTypes));
        };

        return new FactoryAccessor(factory.parameterIds(), handle, () -> parameterTypes);
    }

    private static Method resolveFactoryMethod(Class<?> definitionType, StudyFactoryDescriptor factory, List<Class<?>> parameterTypes) {
        List<Method> candidates = new ArrayList<>();
        for (Method method : definitionType.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())
                    && method.getName().equals(factory.memberName())
                    && method.getParameterCount() == parameterTypes.size()
                    && parametersMatch(method.getParameterTypes(), parameterTypes)) {
                candidates.add(method);
            }
        }
        if (candidates.size() != 1)
            throw new IllegalStateException("Unable to resolve study factory method for " + definitionType.getName() + '#' + factory.memberName());
        return candidates.getFirst();
    }

    private static Constructor<?> resolveConstructor(Class<?> definitionType, StudyFactoryDescriptor factory, List<Class<?>> parameterTypes) {
        List<Constructor<?>> candidates = new ArrayList<>();
        for (Constructor<?> constructor : definitionType.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == parameterTypes.size()
                    && parametersMatch(constructor.getParameterTypes(), parameterTypes)) {
                candidates.add(constructor);
            }
        }
        if (candidates.size() != 1)
            throw new IllegalStateException("Unable to resolve study factory constructor for " + definitionType.getName());
        return candidates.getFirst();
    }

    private static boolean parametersMatch(Class<?>[] declaredTypes, List<Class<?>> parameterTypes) {
        if (declaredTypes.length != parameterTypes.size())
            return false;
        for (int i = 0; i < declaredTypes.length; i++) {
            if (!wrap(declaredTypes[i]).isAssignableFrom(wrap(parameterTypes.get(i))))
                return false;
        }
        return true;
    }

    private static OutputAccessor compileOutput(StudyDescriptor descriptor, StudyOutputDescriptor output) {
        MethodHandle handle = switch (output.target()) {
            case METHOD -> instanceMethodHandle(resolveOutputMethod(descriptor.implementationType(), output.memberName()));
            case FIELD -> fieldHandle(resolveField(descriptor.implementationType(), output.memberName()));
        };
        return new OutputAccessor(output.id(), handle);
    }

    private static Method resolveOutputMethod(Class<?> implementationType, String memberName) {
        for (Class<?> current = implementationType; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(memberName) && method.getParameterCount() == 0)
                    return method;
            }
        }
        throw new IllegalStateException("Unable to resolve study output method: " + implementationType.getName() + '#' + memberName);
    }

    private static Field resolveField(Class<?> implementationType, String memberName) {
        for (Class<?> current = implementationType; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(memberName);
            } catch (NoSuchFieldException ignored) {
                // Continue up the hierarchy.
            }
        }
        throw new IllegalStateException("Unable to resolve study output field: " + implementationType.getName() + '#' + memberName);
    }

    private static Object resolveInput(StudyDescriptor descriptor, CandleSeries dataset, Map<String, ?> uiParameters) {
        return switch (descriptor.factory().inputKind()) {
            case CANDLES -> dataset;
            case CLOSES -> dataset.closes();
            case PRICE_FIELD -> {
                String parameterId = descriptor.factory().inputParameter();
                CandleField field = (CandleField) StudyParameterSupport.coerceUiValue(descriptor.parameter(parameterId), uiParameters.get(parameterId));
                yield dataset.mapToDouble(field);
            }
        };
    }

    private static int inputLength(Object input) {
        return switch (input) {
            case CandleSeries candles -> candles.length();
            case DoubleSeries values -> values.length();
            default -> throw new IllegalStateException("Unsupported study input: " + input.getClass().getName());
        };
    }

    private static void accept(Object input, int index, Object indicator, StudyInputKind inputKind) {
        switch (inputKind) {
            case CANDLES -> ((java.util.function.Consumer<Candle>) indicator).accept(((CandleSeries) input).get(index));
            case CLOSES, PRICE_FIELD -> ((DoubleConsumer) indicator).accept(((DoubleSeries) input).get(index));
        }
    }

    private static MethodHandle staticMethodHandle(Method method) {
        try {
            method.setAccessible(true);
            return MethodHandles.privateLookupIn(method.getDeclaringClass(), MethodHandles.lookup())
                    .unreflect(method)
                    .asType(MethodType.methodType(Object.class, method.getParameterTypes()));
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Cannot access study static method: " + method, ex);
        }
    }

    private static MethodHandle instanceMethodHandle(Method method) {
        try {
            method.setAccessible(true);
            return MethodHandles.privateLookupIn(method.getDeclaringClass(), MethodHandles.lookup())
                    .unreflect(method)
                    .asType(MethodType.methodType(Object.class, Object.class));
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Cannot access study method: " + method, ex);
        }
    }

    private static MethodHandle constructorHandle(Constructor<?> constructor) {
        try {
            constructor.setAccessible(true);
            return MethodHandles.privateLookupIn(constructor.getDeclaringClass(), MethodHandles.lookup())
                    .unreflectConstructor(constructor)
                    .asType(MethodType.methodType(Object.class, constructor.getParameterTypes()));
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Cannot access study constructor: " + constructor, ex);
        }
    }

    private static MethodHandle fieldHandle(Field field) {
        try {
            field.setAccessible(true);
            return MethodHandles.privateLookupIn(field.getDeclaringClass(), MethodHandles.lookup())
                    .unreflectGetter(field)
                    .asType(MethodType.methodType(Object.class, Object.class));
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Cannot access study field: " + field, ex);
        }
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive())
            return type;
        return switch (type.getName()) {
            case "boolean" -> Boolean.class;
            case "byte" -> Byte.class;
            case "short" -> Short.class;
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "float" -> Float.class;
            case "double" -> Double.class;
            case "char" -> Character.class;
            case "void" -> Void.class;
            default -> type;
        };
    }

    public record StudyEvaluation(SequencedMap<String, DoubleSeries> outputs) {
        public StudyEvaluation {
            outputs = outputs == null ? Collections.emptyNavigableMap() : outputs;
        }
    }

    private record CompiledStudy(FactoryAccessor factory, List<OutputAccessor> outputs) {
    }

    private record FactoryAccessor(List<String> parameterIds, MethodHandle handle, Supplier<List<Class<?>>> parameterTypesSupplier) {
        Object create(Map<String, ?> uiParameters) {
            try {
                List<Object> arguments = new ArrayList<>(parameterIds.size());
                List<Class<?>> parameterTypes = parameterTypesSupplier.get();
                for (int index = 0; index < parameterIds.size(); index++) {
                    String parameterId = parameterIds.get(index);
                    Object value = uiParameters.get(parameterId);
                    Class<?> parameterType = parameterTypes.get(index);
                    arguments.add(parameterType.isInstance(value) || value == null ? value : value);
                }
                return handle.invokeWithArguments(arguments);
            } catch (Throwable throwable) {
                throw new IllegalStateException("Cannot instantiate study", throwable);
            }
        }
    }

    private record OutputAccessor(String id, MethodHandle handle) {
        double extract(Object indicator) {
            try {
                Object value = handle.invoke(indicator);
                return switch (value) {
                    case null -> Double.NaN;
                    case Number number -> number.doubleValue();
                    case Boolean bool -> bool ? 1.0d : 0.0d;
                    case Enum<?> e -> e.ordinal();
                    default -> throw new IllegalStateException("Unsupported study output type: " + value.getClass().getName());
                };
            } catch (Throwable throwable) {
                throw new IllegalStateException("Cannot extract study output: " + id, throwable);
            }
        }
    }
}

