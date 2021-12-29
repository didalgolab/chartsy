package one.chartsy.ui.swing;

import javax.swing.JTree;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.TreeModel;

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
                if (1 == model.getChildCount(pathNode))
                    tree.expandPath(event.getPath().pathByAddingChild(model.getChild(pathNode, 0)));
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
