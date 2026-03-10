package one.chartsy.ui.chart.internal;

import one.chartsy.study.StudyColor;
import one.chartsy.study.StudyDescriptor;
import one.chartsy.study.StudyParameterDescriptor;
import one.chartsy.study.StudyParameterType;

import one.chartsy.ui.chart.BasicStrokes;

import java.awt.Color;
import java.awt.Stroke;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class StudyParameterSupport {
    private StudyParameterSupport() {
    }

    public static LinkedHashMap<String, Object> createDefaultUiParameters(StudyDescriptor descriptor) {
        var parameters = new LinkedHashMap<String, Object>();
        descriptor.parameters().forEach((id, parameter) -> parameters.put(id, parseDefaultUiValue(parameter)));
        return parameters;
    }

    public static LinkedHashMap<String, Object> copyUiParameters(StudyDescriptor descriptor, Map<String, ?> values) {
        var parameters = createDefaultUiParameters(descriptor);
        if (values == null)
            return parameters;

        descriptor.parameters().forEach((id, parameter) -> {
            if (values.containsKey(id))
                parameters.put(id, coerceUiValue(parameter, values.get(id)));
        });
        return parameters;
    }

    public static Class<?> uiValueType(StudyParameterDescriptor parameter) {
        return switch (parameter.type()) {
            case COLOR -> Color.class;
            case STROKE -> Stroke.class;
            case BOOLEAN -> Boolean.class;
            case INTEGER -> Integer.class;
            case DOUBLE -> Double.class;
            case ENUM -> parameter.enumType() != Void.class ? parameter.enumType() : parameter.effectiveValueType();
            case STRING, AUTO -> parameter.effectiveValueType();
        };
    }

    public static Object parseDefaultUiValue(StudyParameterDescriptor parameter) {
        String defaultValue = parameter.defaultValue();
        return switch (parameter.type()) {
            case COLOR -> toAwtColor(StudyColor.parse(defaultValue));
            case STROKE -> toStroke(defaultValue);
            case BOOLEAN -> Boolean.parseBoolean(defaultValue);
            case INTEGER -> defaultValue.isBlank() ? 0 : Integer.parseInt(defaultValue);
            case DOUBLE -> defaultValue.isBlank() ? 0.0d : Double.parseDouble(defaultValue);
            case ENUM -> parseEnum(parameter.enumType(), defaultValue);
            case STRING, AUTO -> defaultValue;
        };
    }

    public static Object coerceUiValue(StudyParameterDescriptor parameter, Object value) {
        if (value == null)
            return parseDefaultUiValue(parameter);

        return switch (parameter.type()) {
            case COLOR -> switch (value) {
                case Color color -> color;
                case StudyColor color -> toAwtColor(color);
                case String text -> toAwtColor(StudyColor.parse(text));
                default -> throw new IllegalArgumentException("Unsupported color value: " + value.getClass().getName());
            };
            case STROKE -> switch (value) {
                case Stroke stroke -> stroke;
                case String name -> toStroke(name);
                default -> throw new IllegalArgumentException("Unsupported stroke value: " + value.getClass().getName());
            };
            case BOOLEAN -> switch (value) {
                case Boolean bool -> bool;
                case String text -> Boolean.parseBoolean(text);
                default -> throw new IllegalArgumentException("Unsupported boolean value: " + value.getClass().getName());
            };
            case INTEGER -> switch (value) {
                case Number number -> number.intValue();
                case String text -> Integer.parseInt(text);
                default -> throw new IllegalArgumentException("Unsupported integer value: " + value.getClass().getName());
            };
            case DOUBLE -> switch (value) {
                case Number number -> number.doubleValue();
                case String text -> Double.parseDouble(text);
                default -> throw new IllegalArgumentException("Unsupported double value: " + value.getClass().getName());
            };
            case ENUM -> {
                Class<?> enumType = parameter.enumType();
                if (enumType == Void.class)
                    throw new IllegalArgumentException("Enum type missing for parameter: " + parameter.id());
                yield switch (value) {
                    case Enum<?> e when enumType.isInstance(e) -> e;
                    case String text -> parseEnum(enumType, text);
                    default -> throw new IllegalArgumentException("Unsupported enum value: " + value.getClass().getName());
                };
            }
            case STRING, AUTO -> Objects.toString(value, "");
        };
    }

    public static LinkedHashMap<String, Object> toPresentationParameters(StudyDescriptor descriptor, Map<String, ?> uiParameters) {
        var parameters = new LinkedHashMap<String, Object>();
        descriptor.parameters().forEach((id, parameter) -> parameters.put(id, toPresentationValue(parameter, uiParameters.get(id))));
        return parameters;
    }

    public static Object toPresentationValue(StudyParameterDescriptor parameter, Object uiValue) {
        Object value = coerceUiValue(parameter, uiValue);
        return switch (parameter.type()) {
            case COLOR -> toStudyColor((Color) value);
            case STROKE -> toStrokeName(value);
            default -> value;
        };
    }

    public static String formatLabel(String template, StudyDescriptor descriptor, Map<String, ?> uiParameters) {
        String resolved = template;
        for (var entry : descriptor.parameters().entrySet()) {
            String token = '{' + entry.getKey() + '}';
            Object value = uiParameters.get(entry.getKey());
            resolved = resolved.replace(token, describeLabelValue(entry.getValue(), value));
        }
        return resolved;
    }

    public static StudyColor toStudyColor(Color color) {
        return StudyColor.of(color.getRGB());
    }

    public static Color toAwtColor(StudyColor color) {
        return new Color(color.rgba(), true);
    }

    public static Color toAwtColor(Object value) {
        return switch (value) {
            case Color color -> color;
            case StudyColor color -> toAwtColor(color);
            case String text -> toAwtColor(StudyColor.parse(text));
            case null -> Color.BLACK;
            default -> throw new IllegalArgumentException("Unsupported color value: " + value.getClass().getName());
        };
    }

    public static Stroke toStroke(Object value) {
        return switch (value) {
            case Stroke stroke -> stroke;
            case String name -> {
                Stroke stroke = BasicStrokes.getStroke(name);
                yield stroke != null ? stroke : BasicStrokes.DEFAULT;
            }
            case null -> BasicStrokes.DEFAULT;
            default -> throw new IllegalArgumentException("Unsupported stroke value: " + value.getClass().getName());
        };
    }

    public static String toStrokeName(Object value) {
        return switch (value) {
            case String name -> name;
            case Stroke stroke -> BasicStrokes.getStrokeName(stroke).orElse("DEFAULT");
            case null -> "DEFAULT";
            default -> throw new IllegalArgumentException("Unsupported stroke value: " + value.getClass().getName());
        };
    }

    private static String describeLabelValue(StudyParameterDescriptor parameter, Object value) {
        Object uiValue = coerceUiValue(parameter, value);
        return switch (parameter.type()) {
            case COLOR -> toStudyColor((Color) uiValue).toHex();
            case STROKE -> toStrokeName(uiValue);
            default -> Objects.toString(uiValue, "");
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Enum<?> parseEnum(Class<?> enumType, String defaultValue) {
        if (enumType == null || enumType == Void.class)
            throw new IllegalArgumentException("Enum type missing");
        if (defaultValue == null || defaultValue.isBlank())
            return ((Class<? extends Enum>) enumType).getEnumConstants()[0];
        return Enum.valueOf((Class<? extends Enum>) enumType, defaultValue);
    }
}
