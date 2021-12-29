/*
 * @(#)CheckBoxTreeCellRenderer.java 8/11/2005
 *
 * Copyright 2002 - 2005 JIDE Software Inc. All rights reserved.
 */
package one.chartsy.ui.swing;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.Serial;
import java.io.Serializable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.border.Border;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

/**
 * Renderers an item in a tree using JCheckBox.
 */
public class CheckBoxTreeCellRenderer extends JPanel implements TreeCellRenderer, Serializable {
    @Serial
    private static final long serialVersionUID = -8393766451222279549L;
    
    /** The checkbox that is used to paint the check box in cell renderer. */
    protected TristateCheckBox checkBox;
    protected JComponent emptyBox;
    protected JCheckBox protoType;

    /** The label which appears after the check box. */
    protected TreeCellRenderer actualTreeRenderer;

    protected JPanel actualTreeRendererHolder;

    protected CheckBoxTreeDecorator decorator;
    
    /**
     * Constructs a default renderer object for an item in a list.
     */
    public CheckBoxTreeCellRenderer(CheckBoxTreeDecorator decorator) {
        this(decorator, null);
    }
    
    public CheckBoxTreeCellRenderer(CheckBoxTreeDecorator decorator, TreeCellRenderer renderer) {
        this(decorator, renderer, null);
    }
    
    public CheckBoxTreeCellRenderer(CheckBoxTreeDecorator decorator, TreeCellRenderer renderer, TristateCheckBox checkBox) {
        super(new BorderLayout(), false);
        this.decorator = decorator;
        protoType = new TristateCheckBox();
        if (checkBox == null) {
            this.checkBox = createCheckBox();
        } else {
            this.checkBox = checkBox;
        }
        emptyBox = (JComponent) Box.createHorizontalStrut(protoType.getPreferredSize().width);
        actualTreeRendererHolder = new JPanel(new BorderLayout(), false);
        actualTreeRendererHolder.setOpaque(false);
        setOpaque(false);
        actualTreeRenderer = renderer;
        //		checkBox.setPreferredSize(new Dimension(
        //				protoType.getPreferredSize().width, protoType.getPreferredSize().height));
        //		_emptyBox.setPreferredSize(new Dimension(
        //				_protoType.getPreferredSize().width, 0));
    }
    
    /**
     * Create the check box in the cell.
     * <p/>
     * By default, it creates a TristateCheckBox and set opaque to false.
     * 
     * @return the check box instance.
     */
    protected TristateCheckBox createCheckBox() {
        TristateCheckBox checkBox = new TristateCheckBox();
        checkBox.setOpaque(false);
        return checkBox;
    }
    
    public TreeCellRenderer getActualTreeRenderer() {
        return actualTreeRenderer;
    }
    
    public void setActualTreeRenderer(TreeCellRenderer actualTreeRenderer) {
        this.actualTreeRenderer = actualTreeRenderer;
    }
    
    public int getLeftChildIndent() {
        return protoType.getPreferredSize().width / 2;
    }
    
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value,
            boolean selected, boolean expanded, boolean leaf, int row,
            boolean hasFocus) {
        //_checkBox.setPreferredSize(new Dimension(
        //		_protoType.getPreferredSize().width, 0));
        //_emptyBox.setPreferredSize(new Dimension(
        //		_protoType.getPreferredSize().width, 0));
        applyComponentOrientation(tree.getComponentOrientation());
        
        TreePath path = tree.getPathForRow(row);
        if (path != null) {
            CheckBoxTreeSelectionModel selectionModel = decorator.getCheckBoxTreeSelectionModel();
            if (selectionModel != null) {
                boolean enabled = tree.isEnabled()
                        && decorator.isCheckBoxEnabled()
                        && decorator.isCheckBoxEnabled(path);
                if (!enabled && !selected && getBackground() != null) {
                    setForeground(getBackground().darker());
                }
                checkBox.setEnabled(enabled);
                updateCheckBoxState(checkBox, path, selectionModel);
            }
        }
        
        if (actualTreeRenderer != null) {
            JComponent treeCellRendererComponent = (JComponent) actualTreeRenderer
                    .getTreeCellRendererComponent(tree, value, selected,
                            expanded, leaf, row, hasFocus);
            Border border = treeCellRendererComponent.getBorder();
            setBorder(border);
            treeCellRendererComponent.setBorder(BorderFactory.createEmptyBorder());
            if (path == null || decorator.isCheckBoxVisible(path)) {
                if (checkBox.getParent() == null) {
                    remove(emptyBox);
                    add(checkBox, BorderLayout.WEST);
                }
            } else {
                if (emptyBox == null) {
                    remove(checkBox);
                    add(emptyBox, BorderLayout.EAST); // expand the tree node size to be the same as the one with check box.
                }
            }
            if (actualTreeRendererHolder.getParent() == null || actualTreeRendererHolder.getComponent(0) != treeCellRendererComponent) {
                remove(actualTreeRendererHolder);
                actualTreeRendererHolder.removeAll();
                actualTreeRendererHolder.add(treeCellRendererComponent, BorderLayout.CENTER);
                add(actualTreeRendererHolder, BorderLayout.CENTER);
            }
        }
        
        return this;
    }
    
    /**
     * Updates the check box state based on the selection in the selection
     * model. By default, we check if the path is selected. If yes, we mark the
     * check box as TristateCheckBox.SELECTED. If not, we will check if the path
     * is partially selected, if yes, we set the check box as null or
     * TristateCheckBox.DONT_CARE to indicate the path is partially selected.
     * Otherwise, we set it to TristateCheckBox.NOT_SELECTED.
     * 
     * @param checkBox
     *            the TristateCheckBox for the particular tree path.
     * @param path
     *            the tree path.
     * @param selectionModel
     *            the CheckBoxTreeSelectionModel.
     */
    protected void updateCheckBoxState(TristateCheckBox checkBox,
            TreePath path, CheckBoxTreeSelectionModel selectionModel) {
        if (selectionModel.isPathSelected(path, selectionModel.isDigIn()))
            checkBox.setState(TristateCheckBox.STATE_SELECTED);
        else
            checkBox.setState(selectionModel.isDigIn()
                    && selectionModel.isPartiallySelected(path) ? TristateCheckBox.STATE_MIXED
                            : TristateCheckBox.STATE_UNSELECTED);
    }
    
    @Override
    public String getToolTipText(MouseEvent event) {
        if (actualTreeRenderer instanceof JComponent) {
            Point p = event.getPoint();
            p.translate(-checkBox.getWidth(), 0);
            MouseEvent newEvent = new MouseEvent(
                    ((JComponent) actualTreeRenderer), event.getID(),
                    event.getWhen(), event.getModifiersEx(), p.x, p.y,
                    event.getClickCount(), event.isPopupTrigger());
            
            String tip = ((JComponent) actualTreeRenderer).getToolTipText(newEvent);
            if (tip != null)
                return tip;
        }
        return super.getToolTipText(event);
    }
    
    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        if (!isDisplayable() && !isValid()) {
            doLayout();
            actualTreeRendererHolder.doLayout();
        }
    }
}
