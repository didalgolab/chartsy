/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.swing;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.plaf.TreeUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * CheckBoxTreeDecorator is a special JTree which uses JCheckBox as the tree
 * renderer. In addition to regular JTree's features, it also allows you select
 * any number of tree nodes in the tree by selecting the check boxes.
 * <p>
 * To select an element, user can mouse click on the check box, or select one or
 * several tree nodes and press SPACE key to toggle the check box selection for
 * all selected tree nodes.
 * <p/>
 * In order to retrieve which tree paths are selected, you need to call
 * {@link #getCheckBoxTreeSelectionModel()}. It will return the selection model
 * that keeps track of which tree paths have been checked. For example
 * {@link CheckBoxTreeSelectionModel#getSelectionPaths()} will give the list of
 * paths which have been checked.
 * 
 */
public class CheckBoxTreeDecorator {
    
    public static final String PROPERTY_CHECKBOX_ENABLED = "checkBoxEnabled";
    public static final String PROPERTY_CLICK_IN_CHECKBOX_ONLY = "clickInCheckBoxOnly";
    public static final String PROPERTY_DIG_IN = "digIn";
    
    private CheckBoxTreeCellRenderer checkBoxTreeCellRenderer;
    
    private final CheckBoxTreeSelectionModel checkBoxTreeSelectionModel;
    
    private boolean _checkBoxEnabled = true;
    private boolean _clickInCheckBoxOnly = true;
    private PropertyChangeListener _modelChangeListener;
    private TristateCheckBox _checkBox;
    private boolean _selectPartialOnToggling;
    
    /** The JTree decorated by this decorator instance. */
    private final JTree tree;
    
    /**
     * Returns the JTree decorated by this decorator.
     * 
     * @return the JTree decorated by this decorator
     */
    public JTree getTree() {
        return tree;
    }
    
    public static CheckBoxTreeDecorator decorate(JTree tree) {
        return new CheckBoxTreeDecorator(tree);
    }
    
    /**
     * Initialize the CheckBoxTreeDecorator.
     */
    private CheckBoxTreeDecorator(JTree tree) {
        this.tree = tree;
        checkBoxTreeSelectionModel = createCheckBoxTreeSelectionModel(tree.getModel());
        checkBoxTreeSelectionModel.setTree(this);
        Handler handler = createHandler();
        insertMouseListener(tree, handler, 0);
        tree.addKeyListener(handler);
        checkBoxTreeSelectionModel.addTreeSelectionListener(handler);
        
        if (_modelChangeListener == null) {
            _modelChangeListener = evt -> {
                if (JTree.SELECTION_MODEL_PROPERTY.equals(evt.getPropertyName()))
                    updateRowMapper();
                if ("model".equals(evt.getPropertyName()) && evt.getNewValue() instanceof TreeModel)
                    checkBoxTreeSelectionModel.setModel((TreeModel) evt.getNewValue());
            };
        }
        tree.addPropertyChangeListener(JTree.SELECTION_MODEL_PROPERTY, _modelChangeListener);
        tree.addPropertyChangeListener("model", _modelChangeListener);
        tree.addPropertyChangeListener(JTree.CELL_RENDERER_PROPERTY,
                evt -> {
                    TreeCellRenderer x = (TreeCellRenderer) evt.getNewValue();
                    if (x != checkBoxTreeCellRenderer) {
                        if (x == null)
                            x = getDefaultRenderer();
                        if (checkBoxTreeCellRenderer != null)
                            checkBoxTreeCellRenderer.setActualTreeRenderer(x);
                        ((JTree) evt.getSource()).setCellRenderer(checkBoxTreeCellRenderer);
                    }
                });
        updateRowMapper();
        tree.setCellRenderer(getCellRenderer());
        TreeUI ui = tree.getUI();
        if (ui instanceof BasicTreeUI)
            ((BasicTreeUI) ui).setLeftChildIndent(getCellRenderer().getLeftChildIndent());
    }
    
    /**
     * Inserts the mouse listener at the particular index in the listeners'
     * chain.
     * 
     * @param component
     * @param l
     * @param index
     */
    public static void insertMouseListener(Component component, MouseListener l, int index) {
        MouseListener[] listeners = component.getMouseListeners();
        for (MouseListener listener : listeners)
            component.removeMouseListener(listener);
        
        for (int i = 0; i < listeners.length; i++) {
            MouseListener listener = listeners[i];
            if (index == i)
                component.addMouseListener(l);
            component.addMouseListener(listener);
        }
        // index is too large, add to the end.
        if (index < 0 || index > listeners.length - 1)
            component.addMouseListener(l);
    }
    
    /**
     * Creates the CheckBoxTreeSelectionModel.
     * 
     * @param model
     *            the tree model.
     * @return the CheckBoxTreeSelectionModel.
     */
    protected CheckBoxTreeSelectionModel createCheckBoxTreeSelectionModel(TreeModel model) {
        return new CheckBoxTreeSelectionModel(model);
    }
    
    /**
     * RowMapper is necessary for contiguous selection.
     */
    private void updateRowMapper() {
        checkBoxTreeSelectionModel.setRowMapper(tree.getSelectionModel().getRowMapper());
    }
    
    private TreeCellRenderer _defaultRenderer;
    
    /**
     * Gets the cell renderer with check box.
     * 
     * @return CheckBoxTreeDecorator's own cell renderer which has the check
     *         box. The actual cell renderer you set by setCellRenderer() can be
     *         accessed by using {@link #getActualCellRenderer()}.
     */
    // @Override
    public CheckBoxTreeCellRenderer getCellRenderer() {
        TreeCellRenderer cellRenderer = getActualCellRenderer();
        if (cellRenderer == null)
            cellRenderer = getDefaultRenderer();
        
        if (checkBoxTreeCellRenderer == null)
            checkBoxTreeCellRenderer = createCellRenderer(cellRenderer);
        else
            checkBoxTreeCellRenderer.setActualTreeRenderer(cellRenderer);
        
        return checkBoxTreeCellRenderer;
    }
    
    private TreeCellRenderer getDefaultRenderer() {
        if (_defaultRenderer == null)
            _defaultRenderer = new DefaultTreeCellRenderer();
        return _defaultRenderer;
    }
    
    /**
     * Gets the actual cell renderer. Since CheckBoxTreeDecorator has its own
     * check box cell renderer, this method will give you access to the actual
     * cell renderer which is either the default tree cell renderer or the cell
     * renderer you set using
     * {@link #setCellRenderer(javax.swing.tree.TreeCellRenderer)}.
     * 
     * @return the actual cell renderer
     */
    public TreeCellRenderer getActualCellRenderer() {
        if (checkBoxTreeCellRenderer != null)
            return checkBoxTreeCellRenderer.getActualTreeRenderer();
        
        TreeCellRenderer renderer = tree.getCellRenderer();
        if (renderer instanceof CheckBoxTreeCellRenderer)
            return ((CheckBoxTreeCellRenderer) renderer).getActualTreeRenderer();
        return renderer;
    }
    
    /**
     * Creates the cell renderer.
     * 
     * @param renderer
     *            the actual renderer for the tree node. This method will return
     *            a cell renderer that use a check box and put the actual
     *            renderer inside it.
     * @return the cell renderer.
     */
    protected CheckBoxTreeCellRenderer createCellRenderer(TreeCellRenderer renderer) {
        final CheckBoxTreeCellRenderer checkBoxTreeCellRenderer = new CheckBoxTreeCellRenderer(this, renderer,
                getCheckBox());
        // tree.addPropertyChangeListener(JTree.CELL_RENDERER_PROPERTY, new
        // PropertyChangeListener() {
        // public void propertyChange(PropertyChangeEvent evt) {
        // TreeCellRenderer checkBoxTreeCellRenderer = (TreeCellRenderer)
        // evt.getNewValue();
        // if (checkBoxTreeCellRenderer != checkBoxTreeCellRenderer) {
        // checkBoxTreeCellRenderer.setActualTreeRenderer(checkBoxTreeCellRenderer);
        // }
        // else {
        // checkBoxTreeCellRenderer.setActualTreeRenderer(null);
        // }
        // }
        // });
        return checkBoxTreeCellRenderer;
    }
    
    /**
     * Creates the mouse listener and key listener used by
     * CheckBoxTreeDecorator.
     * 
     * @return the Handler.
     */
    protected Handler createHandler() {
        return new Handler(this);
    }
    
    /**
     * Get the CheckBox used for CheckBoxTreeCellRenderer.
     * 
     * @see #setCheckBox(TristateCheckBox)
     * @return the check box.
     */
    public TristateCheckBox getCheckBox() {
        return _checkBox;
    }
    
    /**
     * Set the CheckBox used for CheckBoxTreeCellRenderer.
     * <p>
     * By default, it's null. CheckBoxTreeCellRenderer then will create a
     * default TristateCheckBox.
     * 
     * @param checkBox
     *            the check box
     */
    public void setCheckBox(TristateCheckBox checkBox) {
        if (_checkBox != checkBox) {
            _checkBox = checkBox;
            checkBoxTreeCellRenderer = null;
            tree.revalidate();
            tree.repaint();
        }
    }
    
    /**
     * Gets the flag indicating if toggling should select or deselect the
     * partially selected node.
     * 
     * @return true if select first. Otherwise false.
     * @see #setSelectPartialOnToggling(boolean)
     */
    public boolean isSelectPartialOnToggling() {
        return _selectPartialOnToggling;
    }
    
    /**
     * Sets the flag indicating if toggling should select or deselect the partially
     * selected node.
     * <p>
     * By default, the value is {@code false} to keep original behavior.
     * 
     * @param selectPartialOnToggling
     *            the flag indicating if the toggling should select or deselect the
     *            partially selected node
     */
    public void setSelectPartialOnToggling(boolean selectPartialOnToggling) {
        _selectPartialOnToggling = selectPartialOnToggling;
    }
    
    /**
     * CheckBoxTreeDecorator's mouse event handler, key event handler and tree
     * selection event handler.
     */
    protected static class Handler implements MouseListener, KeyListener, TreeSelectionListener {
        protected CheckBoxTreeDecorator _tree;
        int _hotspot = new JCheckBox().getPreferredSize().width;
        private int _toggleCount = -1;
        
        /**
         * The constructor.
         * 
         * @param tree
         *            the CheckBoxTreeDecorator
         */
        public Handler(CheckBoxTreeDecorator tree) {
            _tree = tree;
        }
        
        /**
         * Gets the tree path according to the mouse event.
         * 
         * @param e
         *            the mouse event
         * @return the tree path the mouse is over. null if no tree node is
         *         under the mouse position.
         */
        protected TreePath getTreePathForMouseEvent(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e))
                return null;
            
            if (!_tree.isCheckBoxEnabled())
                return null;
            
            TreePath path = _tree.getTree().getPathForLocation(e.getX(), e.getY());
            if (path == null)
                return null;
            
            if (clicksInCheckBox(e, path) || !_tree.isClickInCheckBoxOnly()) {
                return path;
            } else {
                return null;
            }
        }
        
        /**
         * Checks if the mouse event happens for the tree path.
         * 
         * @param e
         *            the mouse event
         * @param path
         *            the tree path
         * @return true if the mouse event need change the state of the tree
         *         node. Otherwise false.
         */
        protected boolean clicksInCheckBox(MouseEvent e, TreePath path) {
            if (!_tree.isCheckBoxVisible(path))
                return false;
            
            Rectangle bounds = _tree.getTree().getPathBounds(path);
            if (_tree.getTree().getComponentOrientation().isLeftToRight()) {
                return e.getX() < bounds.x + _hotspot;
            } else {
                return e.getX() > bounds.x + bounds.width - _hotspot;
            }
        }
        
        private TreePath preventToggleEvent(MouseEvent e) {
            TreePath pathForMouseEvent = getTreePathForMouseEvent(e);
            if (pathForMouseEvent != null) {
                int toggleCount = _tree.getTree().getToggleClickCount();
                if (toggleCount != -1) {
                    _toggleCount = toggleCount;
                    _tree.getTree().setToggleClickCount(-1);
                }
            }
            return pathForMouseEvent;
        }
        
        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.isConsumed())
                return;
            
            preventToggleEvent(e);
        }
        
        @Override
        public void mousePressed(MouseEvent e) {
            if (e.isConsumed())
                return;
            
            TreePath path = preventToggleEvent(e);
            if (path != null) {
                toggleSelections(new TreePath[] { path });
                e.consume();
            }
        }
        
        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.isConsumed())
                return;
            
            TreePath path = preventToggleEvent(e);
            if (path != null)
                e.consume();
            if (_toggleCount != -1)
                _tree.getTree().setToggleClickCount(_toggleCount);
        }
        
        @Override
        public void mouseEntered(MouseEvent e) {
        }
        
        @Override
        public void mouseExited(MouseEvent e) {
        }
        
        @Override
        public void keyPressed(KeyEvent e) {
            if (e.isConsumed())
                return;
            
            if (!_tree.isCheckBoxEnabled())
                return;
            
            if (e.getModifiers() == 0 && e.getKeyChar() == KeyEvent.VK_SPACE)
                toggleSelections();
        }
        
        @Override
        public void keyTyped(KeyEvent e) {
        }
        
        @Override
        public void keyReleased(KeyEvent e) {
        }
        
        @Override
        public void valueChanged(TreeSelectionEvent e) {
            _tree.getTree().treeDidChange();
        }
        
        /**
         * Toggles the selected paths' selection state.
         */
        protected void toggleSelections() {
            TreePath[] treePaths = _tree.getTree().getSelectionPaths();
            toggleSelections(treePaths);
        }
        
        private void toggleSelections(TreePath[] treePaths) {
            if (treePaths == null || treePaths.length == 0 || !_tree.getTree().isEnabled())
                return;
            if (treePaths.length == 1 && !_tree.isCheckBoxEnabled(treePaths[0]))
                return;
            
            CheckBoxTreeSelectionModel selectionModel = _tree.getCheckBoxTreeSelectionModel();
            List<TreePath> pathToAdded = new ArrayList<>();
            List<TreePath> pathToRemoved = new ArrayList<>();
            for (TreePath treePath : treePaths) {
                boolean selected = selectionModel.isPathSelected(treePath, selectionModel.isDigIn());
                if (selected) {
                    pathToRemoved.add(treePath);
                } else {
                    if (!_tree.isSelectPartialOnToggling() && selectionModel.isPartiallySelected(treePath)) {
                        TreePath[] selectionPaths = selectionModel.getSelectionPaths();
                        if (selectionPaths != null)
                            for (TreePath selectionPath : selectionPaths)
                                if (selectionModel.isDescendant(selectionPath, treePath))
                                    pathToRemoved.add(selectionPath);
                        
                    } else {
                        pathToAdded.add(treePath);
                    }
                }
            }
            selectionModel.removeTreeSelectionListener(this);
            try {
                if (pathToAdded.size() > 0)
                    selectionModel.addSelectionPaths(pathToAdded.toArray(new TreePath[pathToAdded.size()]));
                if (pathToRemoved.size() > 0)
                    selectionModel.removeSelectionPaths(pathToRemoved.toArray(new TreePath[pathToRemoved.size()]));
            } finally {
                selectionModel.addTreeSelectionListener(this);
                _tree.getTree().treeDidChange();
            }
        }
    }
    
    // @Override
    // public TreePath getNextMatch(String prefix, int startingRow,
    // Position.Bias bias) {
    // return null;
    // }
    
    /**
     * Gets the selection model for the check boxes. To retrieve the state of
     * check boxes, you should use this selection model.
     * 
     * @return the selection model for the check boxes.
     */
    public CheckBoxTreeSelectionModel getCheckBoxTreeSelectionModel() {
        return checkBoxTreeSelectionModel;
    }
    
    /**
     * Gets the value of property checkBoxEnabled. If true, user can click on
     * check boxes on each tree node to select and deselect. If false, user
     * can't click but you as developer can programmatically call API to
     * select/deselect it.
     * 
     * @return the value of property checkBoxEnabled.
     */
    public boolean isCheckBoxEnabled() {
        return _checkBoxEnabled;
    }
    
    /**
     * Sets the value of property checkBoxEnabled.
     * 
     * @param checkBoxEnabled
     *            true to allow to check the check box. False to disable it
     *            which means user can see whether a row is checked or not but
     *            they cannot change it.
     */
    public void setCheckBoxEnabled(boolean checkBoxEnabled) {
        if (checkBoxEnabled != _checkBoxEnabled) {
            Boolean oldValue = _checkBoxEnabled ? Boolean.TRUE : Boolean.FALSE;
            Boolean newValue = checkBoxEnabled ? Boolean.TRUE : Boolean.FALSE;
            _checkBoxEnabled = checkBoxEnabled;
            tree.firePropertyChange(PROPERTY_CHECKBOX_ENABLED, oldValue,
                    newValue);
            tree.repaint();
        }
    }
    
    /**
     * Checks if check box is enabled. There is no setter for it. The only way
     * is to override this method to return true or false.
     * <p/>
     * However, in digIn mode, user can still select the disabled node by
     * selecting all children nodes of that node. Also if user selects the
     * parent node, the disabled children nodes will be selected too.
     * 
     * @param path
     *            the tree path.
     * @return true or false. If false, the check box on the particular tree
     *         path will be disabled.
     */
    public boolean isCheckBoxEnabled(TreePath path) {
        return true;
    }
    
    /**
     * Checks if check box is visible. There is no setter for it. The only way
     * is to override this method to return true or false.
     * 
     * @param path
     *            the tree path.
     * @return true or false. If false, the check box on the particular tree
     *         path will be disabled.
     */
    public boolean isCheckBoxVisible(TreePath path) {
        return true;
    }
    
    /**
     * Gets the dig-in mode. If the CheckBoxTreeDecorator is in dig-in mode,
     * checking the parent node will check all the children. Correspondingly,
     * getSelectionPaths() will only return the parent tree path. If not in
     * dig-in mode, each tree node can be checked or unchecked independently
     * 
     * @return true or false.
     */
    public boolean isDigIn() {
        return getCheckBoxTreeSelectionModel().isDigIn();
    }
    
    /**
     * Sets the dig-in mode. If the CheckBoxTreeDecorator is in dig-in mode,
     * checking the parent node will check all the children. Correspondingly,
     * getSelectionPaths() will only return the parent tree path. If not in
     * dig-in mode, each tree node can be checked or unchecked independently
     * 
     * @param digIn
     *            the new digIn mode.
     */
    public void setDigIn(boolean digIn) {
        boolean old = isDigIn();
        if (old != digIn) {
            getCheckBoxTreeSelectionModel().setDigIn(digIn);
            tree.firePropertyChange(PROPERTY_DIG_IN, old, digIn);
        }
    }
    
    /**
     * Gets the value of property clickInCheckBoxOnly. If true, user can click on
     * check boxes on each tree node to select and deselect. If false, user can't
     * click but you as developer can programmatically call API to select/deselect
     * it.
     * 
     * @return the value of property clickInCheckBoxOnly.
     */
    public boolean isClickInCheckBoxOnly() {
        return _clickInCheckBoxOnly;
    }
    
    /**
     * Sets the value of property clickInCheckBoxOnly.
     * 
     * @param clickInCheckBoxOnly
     *            true to allow to check the check box. False to disable it
     *            which means user can see whether a row is checked or not but
     *            they cannot change it.
     */
    public void setClickInCheckBoxOnly(boolean clickInCheckBoxOnly) {
        if (clickInCheckBoxOnly != _clickInCheckBoxOnly) {
            boolean old = _clickInCheckBoxOnly;
            _clickInCheckBoxOnly = clickInCheckBoxOnly;
            tree.firePropertyChange(PROPERTY_CLICK_IN_CHECKBOX_ONLY, old, _clickInCheckBoxOnly);
        }
    }
}
