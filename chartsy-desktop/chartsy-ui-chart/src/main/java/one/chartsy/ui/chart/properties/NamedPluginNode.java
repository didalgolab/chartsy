/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.properties;

import one.chartsy.ui.chart.ChartPlugin;
import one.chartsy.ui.chart.internal.ChartPluginParameter;
import one.chartsy.ui.chart.internal.ChartPluginParameterUtils;

import java.beans.PropertyEditor;

import org.openide.ErrorManager;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;

/**
 * Provides the {@code Node} representation of a chart plugin (e.g. chart indicator, overlay, etc...).
 *
 * @author Mariusz Bernacki
 */
public class NamedPluginNode<T extends ChartPlugin<T>> extends AbstractPropertiesNode {
    /** The plugin represented by the node. */
    private final T plugin;

    @SuppressWarnings("rawtypes")
    private static final class ParameterProperty extends Node.Property {
        private final ChartPluginParameter parameter;

        @SuppressWarnings("unchecked")
        ParameterProperty(ChartPluginParameter parameter) {
            super(parameter.valueType());
            this.parameter = parameter;
        }

        @Override
        public void restoreDefaultValue() {
            parameter.restoreDefaultValue();
        }

        @Override
        public boolean supportsDefaultValue() {
            return parameter.supportsDefaultValue();
        }

        @Override
        public boolean canRead() {
            return parameter.canRead();
        }

        @Override
        public Object getValue() {
            return parameter.getValue();
        }

        @Override
        public boolean canWrite() {
            return parameter.canWrite();
        }

        @Override
        public void setValue(Object val) {
            parameter.setValue(val);
        }

        @Override
        public PropertyEditor getPropertyEditor() {
            Class<? extends PropertyEditor> propertyEditorClass = parameter.propertyEditorClass();
            if (propertyEditorClass != PropertyEditor.class) {
                try {
                    return propertyEditorClass.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException ex) {
                    ErrorManager.getDefault().notify(ex);
                }
            }
            return super.getPropertyEditor();
        }
    }

    public NamedPluginNode(T plugin) {
        super(plugin.getName() + " Properties");
        this.plugin = plugin;
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = new Sheet();
        sheet.put(getSets()[0]);
        return sheet;
    }

    @Override
    public Sheet.Set[] getSets() {
        Sheet.Set[] sets = new Sheet.Set[1];
        Sheet.Set set = getPropertiesSet();
        sets[0] = set;

        for (ChartPluginParameter parameter : ChartPluginParameterUtils.getParameters(plugin)) {
            ParameterProperty property = new ParameterProperty(parameter);
            property.setName(parameter.id());
            property.setDisplayName(parameter.name());
            property.setShortDescription(parameter.description().isBlank()
                    ? "Sets the " + parameter.name()
                    : parameter.description());
            set.put(property);
        }
        return sets;
    }
}
