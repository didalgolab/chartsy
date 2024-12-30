/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.properties;

import java.awt.Stroke;
import java.beans.PropertyEditor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import one.chartsy.core.NamedPlugin;
import one.chartsy.ui.chart.ChartPlugin.Parameter;
import one.chartsy.ui.chart.properties.editor.StrokePropertyEditor;
import org.openide.ErrorManager;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;

/**
 * Provides the {@code Node} representation of a named plugin (e.g. chart indicator, overlay, etc...).
 *
 * @author Mariusz Bernacki
 */
public class NamedPluginNode<T extends NamedPlugin<T>> extends AbstractPropertiesNode {
    /** The indicator of the node. */
    private final T plugin;
    
    
    /** Support for properties from Java Reflection. */
    public static class Reflection extends Node.Property {
        /** The instance of a bean. */
        private final Object instance;
        /** The property field. */
        private final Field field;
        /** class of property editor */
        private Class<? extends PropertyEditor> propertyEditorClass;
        /** The property default value. */
        private Object defaultValue;
        
        /**
         * Create a support with method objects specified. The methods must be
         * public.
         * 
         * @param instance
         *            (Bean) object to work on
         * @param field
         *            the property field
         */
        public Reflection(Object instance, Field field) {
            super(field.getType());
            this.instance = instance;
            this.field = field;
            
            // extract property default value
            try {
                this.defaultValue = getValue();
            } catch (IllegalAccessException e) {
                // ignore
            } catch (IllegalArgumentException e) {
                // ignore
            }
        }
        
        @Override
        public void restoreDefaultValue() throws IllegalAccessException {
            setValue(defaultValue);
        }
        
        @Override
        public boolean supportsDefaultValue() {
            return true;
        }
        
        /**
         * Can read the value of the property.
         * 
         * @return <CODE>true</CODE> if the read of the value is supported
         */
        @Override
        public boolean canRead() {
            return true;
        }
        
        /**
         * Getter for the value.
         * 
         * @return the value of the property
         * @exception IllegalAccessException
         *                cannot access the called method
         * @exception IllegalArgumentException
         *                wrong argument
         */
        @Override
        public Object getValue() throws IllegalAccessException, IllegalArgumentException {
            try {
                try {
                    return field.get(instance);
                } catch (IllegalAccessException ex) {
                    field.setAccessible(true);
                    return field.get(instance);
                }
            } catch (IllegalArgumentException iae) {
                //Provide a better message for debugging
                StringBuilder sb = new StringBuilder("Attempted to read field ");
                sb.append(field.getName());
                sb.append(" from class ");
                sb.append(field.getDeclaringClass().getName());
                sb.append(" on an instance of ");
                sb.append(instance.getClass().getName());
                sb.append(" Problem:");
                sb.append(iae.getMessage());
                IllegalArgumentException nue = new IllegalArgumentException (sb.toString());
                ErrorManager.getDefault().annotate(nue, iae);
                throw nue;
            }
        }
        
        /**
         * Can write the value of the property.
         * 
         * @return <CODE>true</CODE> if the read of the value is supported
         */
        @Override
        public boolean canWrite() {
            return !Modifier.isFinal(field.getModifiers());
        }
        
        /**
         * Setter for the value.
         * 
         * @param val
         *            the value of the property
         * @exception IllegalAccessException
         *                cannot access the called method
         * @exception IllegalArgumentException
         *                wrong argument
         * @exception InvocationTargetException
         *                an exception during invocation
         */
        @Override
        public void setValue(Object val) throws IllegalAccessException, IllegalArgumentException {
            try {
                field.set(instance, val);
            } catch (IllegalAccessException ex) {
                field.setAccessible(true); 
                field.set(instance, val);
            }
        }
        
        /**
         * Returns property editor for this property.
         * 
         * @return the property editor or <CODE>null</CODE> if there should not
         *         be any editor.
         */
        @Override
        public PropertyEditor getPropertyEditor() {
            if (propertyEditorClass != null) {
                try {
                    return (PropertyEditor) propertyEditorClass.newInstance();
                } catch (InstantiationException e) {
                    ErrorManager.getDefault().notify(e);
                } catch (IllegalAccessException e) {
                    ErrorManager.getDefault().notify(e);
                }
            }
            return super.getPropertyEditor();
        }
        
        /** Set the property editor explicitly.
         * @param clazz class type of the property editor
         */
        public void setPropertyEditorClass(Class<? extends PropertyEditor> clazz) {
            propertyEditorClass = clazz;
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
        
        Field[] parameters = plugin.getClass().getFields();
        for (Field parameter : parameters) {
            Parameter ann = parameter.getAnnotation(Parameter.class);
            
            if (ann != null) {
                Reflection reflection = new Reflection(plugin, parameter);
                reflection.setName(ann.name());
                reflection.setDisplayName(ann.name());
                reflection.setShortDescription(ann.description());
                if (reflection.getShortDescription().length() == 0)
                    reflection.setShortDescription("Sets the " + ann.name());
                
                if (ann.propertyEditorClass() != PropertyEditor.class)
                    reflection.setPropertyEditorClass(ann.propertyEditorClass());
                else if (parameter.getType() == Stroke.class)
                    reflection.setPropertyEditorClass(StrokePropertyEditor.class);
                //else if (parameter.getType() == PriceType.class)
                //    reflection.setPropertyEditorClass(PriceTypePropertyEditor.class);
                
                set.put(reflection);
            }
        }
        return sets;
    }
}
