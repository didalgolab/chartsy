/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.properties;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Externalizable;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Logger;

import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.PropertySupport;
import org.openide.nodes.Sheet;
import org.openide.util.lookup.Lookups;

public abstract class AbstractPropertiesNode extends AbstractNode implements PropertyChangeListener, Externalizable {
    
    protected static final Logger LOG = Logger.getLogger(AbstractPropertiesNode.class.getPackage().getName());
    
    public AbstractPropertiesNode(String name) {
        super(Children.LEAF);
        setDisplayName(name);
    }
    
    public AbstractPropertiesNode(String name, AbstractPropertyListener listener) {
        super(Children.LEAF, Lookups.singleton(listener));
        setDisplayName(name);
        listener.addPropertyChangeListener(this);
    }
    
    public AbstractPropertyListener getAbstractPropertyListener() {
        return getLookup().lookup(AbstractPropertyListener.class);
    }
    
    public Sheet.Set[] getSets() {
        return new Sheet.Set[] { getPropertiesSet() };
    }
    
    protected Sheet.Set getPropertiesSet() {
        return getPropertiesSet(getDisplayName());
    }
    
    protected Sheet.Set getPropertiesSet(String name) {
        return getPropertiesSet(name, name);
    }
    
    protected Sheet.Set getPropertiesSet(String name, String description) {
        Sheet.Set set = new Sheet.Set();
        set.setName(name);
        set.setDisplayName(name);
        set.setShortDescription(description);
        return set;
    }
    
    protected PropertySupport.Reflection getProperty(String name, String description, Class clazz, Class property,
            Class propertyEditor, String getMethod, String setMethod, final Object defaultValue)
                    throws NoSuchMethodException {
        @SuppressWarnings(value = "unchecked")
        PropertySupport.Reflection reflection = new PropertySupport.Reflection(getLookup().lookup(clazz), property, getMethod, setMethod) {
            @Override
            public void restoreDefaultValue() throws IllegalAccessException, InvocationTargetException {
                super.setValue(defaultValue);
            }
            
            @Override
            public boolean supportsDefaultValue() {
                return true;
            }
        };
        reflection.setName(name);
        reflection.setDisplayName(name);
        reflection.setShortDescription(description);
        reflection.setPropertyEditorClass(propertyEditor);
        
        return reflection;
    }
    
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        firePropertySetsChange(null, getPropertySets());
    }
    
    @Override
    public void writeExternal(ObjectOutput out) { }
    
    @Override
    public void readExternal(ObjectInput in) { }
}
