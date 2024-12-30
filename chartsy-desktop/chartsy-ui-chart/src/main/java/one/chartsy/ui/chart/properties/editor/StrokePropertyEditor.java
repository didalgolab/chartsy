/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.properties.editor;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.ActionListener;
import java.beans.PropertyEditor;
import java.beans.PropertyEditorSupport;

import javax.swing.JComponent;
import javax.swing.KeyStroke;

import org.openide.explorer.propertysheet.ExPropertyEditor;
import org.openide.explorer.propertysheet.InplaceEditor;
import org.openide.explorer.propertysheet.PropertyEnv;
import org.openide.explorer.propertysheet.PropertyModel;

public class StrokePropertyEditor extends PropertyEditorSupport implements ExPropertyEditor, InplaceEditor.Factory {
    
    private InplaceEditor ed = null;
    
    public StrokePropertyEditor() {}
    
    @Override
    public void paintValue(Graphics g, Rectangle box) {
        Graphics2D g2 = (Graphics2D)g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        Stroke old = g2.getStroke();
        
        g.setColor(Color.WHITE);
        g2.fill(box);
        
        Stroke stroke = (Stroke) getValue();
        g2.setStroke(stroke);
        g2.setColor(Color.BLACK);
        
        int x = 5;
        while (box.width <= 2 * x) {
            x--;
        }
        
        g2.drawLine(x, box.height / 2, box.width - x, box.height / 2);
        
        g2.setStroke(old);
    }

    @Override
    public void attachEnv(PropertyEnv env) { env.registerInplaceEditorFactory(this); }

    @Override
    public InplaceEditor getInplaceEditor() {
        if (ed == null) ed = new Inplace();
        return ed;
    }

    @Override
    public boolean isPaintable() { return true; }
    
    private static class Inplace implements InplaceEditor {
        
        private final StrokeComboBox comboBox = new StrokeComboBox();
        private PropertyEditor editor = null;
        private PropertyModel model;
        
        @Override
        public void connect(PropertyEditor propertyEditor, PropertyEnv env) { editor = propertyEditor; reset(); }
        @Override
        public JComponent getComponent() { return comboBox; }
        @Override
        public void clear() { editor = null; model = null; }
        @Override
        public Object getValue() { return comboBox.getSelectedItem(); }
        @Override
        public void setValue(Object object) { int i = (Integer) object; comboBox.setSelectedItem(object); }
        @Override
        public boolean supportsTextEntry() { return false; }
        @Override
        public void reset() { if (editor.getValue() != null) comboBox.setSelectedItem(editor.getValue()); }
        @Override
        public void addActionListener(ActionListener arg0) {}
        @Override
        public void removeActionListener(ActionListener arg0) {}
        @Override
        public KeyStroke[] getKeyStrokes() { return new KeyStroke[0]; }
        @Override
        public PropertyEditor getPropertyEditor() { return editor; }
        @Override
        public PropertyModel getPropertyModel() { return model; }
        @Override
        public void setPropertyModel(PropertyModel propertyModel) { this.model = propertyModel; }
        @Override
        public boolean isKnownComponent(Component component) { return component == comboBox || comboBox.isAncestorOf(component); }
        
    }
    
}
