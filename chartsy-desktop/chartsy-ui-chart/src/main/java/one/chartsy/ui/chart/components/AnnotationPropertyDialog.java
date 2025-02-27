/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.components;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import one.chartsy.ui.chart.Annotation;
import one.chartsy.ui.chart.properties.AnnotationNode;
import org.openide.explorer.propertysheet.PropertySheet;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;

/**
 * 
 * 
 * @author Mariusz Bernacki
 *
 */
public class AnnotationPropertyDialog extends JDialog {
    
    /** The annotation which properties are being explored. */
    private Annotation annotation;
    
    
    /** Creates new form AnnotationPropertyDialog */
    public AnnotationPropertyDialog() {
        super(WindowManager.getDefault().getMainWindow());
        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setModalityType(ModalityType.DOCUMENT_MODAL);
        initComponents();
        registerKeyboardActions();
    }
    
    protected void registerKeyboardActions() {
        getRootPane().registerKeyboardAction(e -> fireWindowClosing(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
    
    protected void fireWindowClosing() {
        dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
    }
    
    public void setAnnotation(Annotation annotation) {
        // create property sheet component
        PropertySheet prop = new PropertySheet();
        prop.setNodes(new Node[] { new AnnotationNode(annotation) });
        getContentPane().add(prop);
        
        // create bottom button panel component
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        getContentPane().add(bottomPanel, BorderLayout.SOUTH);
        
        // create OK (dialog dismiss) button
        JButton b = new JButton("OK");
        b.addActionListener(event -> fireWindowClosing());
        bottomPanel.add(b);
        
        pack();
    }
    
    private void initComponents() {
        setTitle(NbBundle.getMessage(AnnotationPropertyDialog.class, "AnnotationPropertyDialog.title"));
    }
}
