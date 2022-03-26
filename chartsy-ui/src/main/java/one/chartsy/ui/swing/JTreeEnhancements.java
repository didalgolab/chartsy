package one.chartsy.ui.swing;

import org.openide.explorer.view.Visualizer;
import org.openide.nodes.*;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.TreeModel;
import java.beans.PropertyChangeEvent;

/**
 * Collects various enhancements applicable to {@code JTree} components.
 * 
 * @author Mariusz Bernacki
 *
 */
public class JTreeEnhancements {
    
    /**
     * Enhances the given tree to allow auto-expanding a child tree node when the
     * parent tree node is expanded and it has the only child.
     * 
     * @param tree
     *            the tree component to enhance
     * @return the listener added to the tree
     */
    public static TreeExpansionListener setSingleChildExpansionPolicy(JTree tree) {
        TreeExpansionListener listener = new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                TreeModel model = tree.getModel();
                Object pathNode = event.getPath().getLastPathComponent();
                if (1 == model.getChildCount(pathNode)) {
                    Object child;
                    tree.expandPath(event.getPath().pathByAddingChild(child = model.getChild(pathNode, 0)));

                    expandLaterIfOnDelayedNode(child,
                            () -> tree.expandPath(event.getPath().pathByAddingChild(model.getChild(pathNode, 0))));
                }
            }

            private void expandLaterIfOnDelayedNode(Object child, Runnable expander) {
                if (!NodeSupport.isVisualizerNode(child))
                    return;

                Node node = Visualizer.findNode(child);
                if (!NodeSupport.isWaitNode(node))
                    return;

                node.addNodeListener(new NodeAdapter() {
                    @Override
                    public void propertyChange(PropertyChangeEvent ev) {
                        if (ev.getPropertyName().equals(Node.PROP_PARENT_NODE) && ev.getOldValue() != null) {
                            Node parent = (Node) ev.getOldValue();
                            NodeListener currListener = this;
                            SwingUtilities.invokeLater(() -> {
                                Children.MUTEX.readAccess(() -> {
                                    node.removeNodeListener(currListener);
                                    if (parent.getChildren().getNodesCount() == 1)
                                        SwingUtilities.invokeLater(expander);
                                });
                            });
                        }
                    }
                });
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
                // do nothing
            }
        };
        
        tree.addTreeExpansionListener(listener);
        return listener;
    }
}
