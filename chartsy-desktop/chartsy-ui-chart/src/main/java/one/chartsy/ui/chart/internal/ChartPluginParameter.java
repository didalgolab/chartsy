package one.chartsy.ui.chart.internal;

import one.chartsy.ui.chart.ChartPlugin;

import java.beans.PropertyEditor;

public interface ChartPluginParameter {
    String id();

    String name();

    String description();

    Class<?> valueType();

    ChartPlugin.Stereotype stereotype();

    Class<? extends PropertyEditor> propertyEditorClass();

    boolean canRead();

    Object getValue();

    boolean canWrite();

    void setValue(Object value);

    boolean supportsDefaultValue();

    void restoreDefaultValue();
}
