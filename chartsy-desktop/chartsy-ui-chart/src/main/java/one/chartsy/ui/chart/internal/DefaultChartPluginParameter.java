package one.chartsy.ui.chart.internal;

import one.chartsy.ui.chart.ChartPlugin;

import java.beans.PropertyEditor;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class DefaultChartPluginParameter implements ChartPluginParameter {
    private final String id;
    private final String name;
    private final String description;
    private final Class<?> valueType;
    private final ChartPlugin.Stereotype stereotype;
    private final Class<? extends PropertyEditor> propertyEditorClass;
    private final Supplier<Object> getter;
    private final Consumer<Object> setter;
    private final Object defaultValue;

    public DefaultChartPluginParameter(String id,
                                String name,
                                String description,
                                Class<?> valueType,
                                ChartPlugin.Stereotype stereotype,
                                Class<? extends PropertyEditor> propertyEditorClass,
                                Supplier<Object> getter,
                                Consumer<Object> setter,
                                Object defaultValue) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.description = description == null ? "" : description;
        this.valueType = Objects.requireNonNull(valueType, "valueType");
        this.stereotype = stereotype == null ? ChartPlugin.Stereotype.NONE : stereotype;
        this.propertyEditorClass = propertyEditorClass == null ? PropertyEditor.class : propertyEditorClass;
        this.getter = Objects.requireNonNull(getter, "getter");
        this.setter = setter;
        this.defaultValue = defaultValue;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public Class<?> valueType() {
        return valueType;
    }

    @Override
    public ChartPlugin.Stereotype stereotype() {
        return stereotype;
    }

    @Override
    public Class<? extends PropertyEditor> propertyEditorClass() {
        return propertyEditorClass;
    }

    @Override
    public boolean canRead() {
        return true;
    }

    @Override
    public Object getValue() {
        return getter.get();
    }

    @Override
    public boolean canWrite() {
        return setter != null;
    }

    @Override
    public void setValue(Object value) {
        if (setter == null)
            throw new IllegalStateException("Parameter is read-only: " + id);
        setter.accept(value);
    }

    @Override
    public boolean supportsDefaultValue() {
        return setter != null;
    }

    @Override
    public void restoreDefaultValue() {
        if (setter != null)
            setter.accept(defaultValue);
    }
}
