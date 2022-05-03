package one.chartsy.ui.actions;

import org.openide.util.ImageUtilities;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class CollapseAllAction extends AbstractAction {

    public CollapseAllAction() {
        putValue(SHORT_DESCRIPTION, "Collapse All");
        putValue(SMALL_ICON, ImageUtilities.loadImageIcon("/one/chartsy/ui/resources/collapse-all.png", true));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        JTree tree = findTree(e.getSource());
        if (tree != null) {
            int row = tree.getRowCount() - 1;
            while (row > 0)
                tree.collapseRow(row--);
        }
    }

    protected JTree findTree(Object o) {
        JTree tree = null;
        if (o instanceof Component c)
            while (c instanceof JComponent && (tree = (JTree)((JComponent) c).getClientProperty(JTree.class)) == null)
                c = c.getParent();

        return tree;
    }
}
