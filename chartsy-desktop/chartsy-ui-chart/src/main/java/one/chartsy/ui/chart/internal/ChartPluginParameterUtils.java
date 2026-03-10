/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.internal;

import one.chartsy.ui.chart.ChartPlugin;
import one.chartsy.ui.chart.StudyBackedChartPlugin;
import one.chartsy.ui.chart.properties.editor.StrokePropertyEditor;

import java.awt.Stroke;
import java.beans.PropertyEditor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parameter helpers for chart plugins, with dynamic-study support first and reflection as a fallback.
 */
public final class ChartPluginParameterUtils {
    private static final Map<Class<?>, List<Field>> PARAMETER_FIELDS_BY_TYPE = new ConcurrentHashMap<>();

    private ChartPluginParameterUtils() {
    }

    public static List<ChartPluginParameter> getParameters(ChartPlugin<?> plugin) {
        Objects.requireNonNull(plugin, "plugin");
        if (plugin instanceof ChartPluginParameterSource parameterSource) {
            return List.copyOf(parameterSource.getChartPluginParameters());
        }
        return createReflectionParameters(plugin);
    }

    public static void copyParameterValues(ChartPlugin<?> source, ChartPlugin<?> target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        if (!Objects.equals(configurationIdentity(source), configurationIdentity(target)))
            throw new IllegalArgumentException("Parameter copy requires matching plugin identities");

        Map<String, ChartPluginParameter> targetParameters = parameterMap(target);
        for (ChartPluginParameter sourceParameter : getParameters(source)) {
            ChartPluginParameter targetParameter = targetParameters.get(sourceParameter.id());
            if (targetParameter != null && targetParameter.canWrite())
                targetParameter.setValue(sourceParameter.getValue());
        }
    }

    public static boolean haveSameParameterValues(ChartPlugin<?> left, ChartPlugin<?> right) {
        if (left == right)
            return true;
        if (left == null || right == null)
            return false;
        if (!Objects.equals(configurationIdentity(left), configurationIdentity(right)))
            return false;

        Map<String, ChartPluginParameter> leftParameters = parameterMap(left);
        Map<String, ChartPluginParameter> rightParameters = parameterMap(right);
        if (!leftParameters.keySet().equals(rightParameters.keySet()))
            return false;

        for (String parameterId : leftParameters.keySet()) {
            Object leftValue = leftParameters.get(parameterId).getValue();
            Object rightValue = rightParameters.get(parameterId).getValue();
            if (!Objects.deepEquals(leftValue, rightValue))
                return false;
        }
        return true;
    }

    private static Map<String, ChartPluginParameter> parameterMap(ChartPlugin<?> plugin) {
        Map<String, ChartPluginParameter> parameters = new LinkedHashMap<>();
        for (ChartPluginParameter parameter : getParameters(plugin))
            parameters.put(parameter.id(), parameter);
        return parameters;
    }

    private static String configurationIdentity(ChartPlugin<?> plugin) {
        return (plugin instanceof StudyBackedChartPlugin studyPlugin)
                ? studyPlugin.getStudyDescriptorId()
                : plugin.getClass().getName();
    }

    private static List<ChartPluginParameter> createReflectionParameters(ChartPlugin<?> plugin) {
        List<ChartPluginParameter> parameters = new ArrayList<>();
        for (Field field : getParameterFields(plugin.getClass())) {
            ChartPlugin.Parameter annotation = field.getAnnotation(ChartPlugin.Parameter.class);
            if (annotation != null)
                parameters.add(createReflectionParameter(plugin, field, annotation));
        }
        return List.copyOf(parameters);
    }

    private static ChartPluginParameter createReflectionParameter(ChartPlugin<?> plugin, Field field, ChartPlugin.Parameter annotation) {
        Object defaultValue = readFieldValue(plugin, field);
        return new DefaultChartPluginParameter(
                field.getName(),
                annotation.name(),
                annotation.description(),
                field.getType(),
                annotation.stereotype(),
                propertyEditorClass(field, annotation),
                () -> readFieldValue(plugin, field),
                Modifier.isFinal(field.getModifiers()) ? null : value -> writeFieldValue(plugin, field, value),
                defaultValue
        );
    }

    private static Class<? extends PropertyEditor> propertyEditorClass(Field field, ChartPlugin.Parameter annotation) {
        if (annotation.propertyEditorClass() != PropertyEditor.class)
            return annotation.propertyEditorClass();
        if (Stroke.class.isAssignableFrom(field.getType()))
            return StrokePropertyEditor.class;
        return PropertyEditor.class;
    }

    private static List<Field> getParameterFields(Class<?> type) {
        return PARAMETER_FIELDS_BY_TYPE.computeIfAbsent(type, ChartPluginParameterUtils::findParameterFields);
    }

    private static List<Field> findParameterFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())
                        && field.getAnnotation(ChartPlugin.Parameter.class) != null)
                    fields.add(field);
            }
        }
        return List.copyOf(fields);
    }

    private static Object readFieldValue(Object source, Field field) {
        try {
            if (!field.canAccess(source))
                field.setAccessible(true);
            return field.get(source);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Cannot read chart plugin field: " + field.getName(), ex);
        }
    }

    private static void writeFieldValue(Object target, Field field, Object value) {
        try {
            if (!field.canAccess(target))
                field.setAccessible(true);
            field.set(target, value);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Cannot write chart plugin field: " + field.getName(), ex);
        }
    }
}
