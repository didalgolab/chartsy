/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.internal;

import one.chartsy.ui.chart.ChartPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflection helpers for chart plugin parameters.
 */
public final class ChartPluginParameterUtils {
    private static final Map<Class<?>, List<Field>> PARAMETER_FIELDS_BY_TYPE = new ConcurrentHashMap<>();

    private ChartPluginParameterUtils() {
    }

    public static List<Field> getParameterFields(Class<?> type) {
        return PARAMETER_FIELDS_BY_TYPE.computeIfAbsent(type, ChartPluginParameterUtils::findParameterFields);
    }

    public static void copyParameterValues(Object source, Object target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        if (source.getClass() != target.getClass())
            throw new IllegalArgumentException("Parameter copy requires identical types");

        for (Field field : getParameterFields(source.getClass()))
            writeFieldValue(target, field, readFieldValue(source, field));
    }

    public static boolean haveSameParameterValues(Object left, Object right) {
        if (left == right)
            return true;
        if (left == null || right == null || left.getClass() != right.getClass())
            return false;

        for (Field field : getParameterFields(left.getClass())) {
            Object leftValue = readFieldValue(left, field);
            Object rightValue = readFieldValue(right, field);
            if (!Objects.deepEquals(leftValue, rightValue))
                return false;
        }
        return true;
    }

    public static Object readFieldValue(Object source, Field field) {
        try {
            if (!field.canAccess(source))
                field.setAccessible(true);
            return field.get(source);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Cannot read chart plugin field: " + field.getName(), ex);
        }
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

    private static void writeFieldValue(Object target, Field field, Object value) {
        try {
            if (!field.canAccess(target))
                field.setAccessible(true);
            field.set(target, value);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Cannot copy chart plugin field: " + field.getName(), ex);
        }
    }
}
