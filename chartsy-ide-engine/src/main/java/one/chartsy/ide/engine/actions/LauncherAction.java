package one.chartsy.ide.engine.actions;

import org.openide.awt.DropDownButtonFactory;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;
import org.openide.util.actions.Presenter;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.util.List;

public class LauncherAction extends AbstractAction implements Presenter.Toolbar, PopupMenuListener {

    private final String command;
    private final Lookup lookup;

    public LauncherAction(String command, String name, Icon icon, Lookup lookup) {
        super(name, icon);
        this.command = command;

        if (lookup == null)
            lookup = LastActivatedWindowLookup.INSTANCE;
        this.lookup = lookup;

        putValue(NAME, name);
        if (icon != null)
            putValue(SMALL_ICON, icon);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        for (Object o : lookup.lookupAll(Object.class)) {
            System.out.println(" ---> " + o.getClass() + ": " + o);
        }

        new Exception("STACK_TRACE").printStackTrace();
    }

    @Override
    public Component getToolbarPresenter() {
        JPopupMenu menu = new JPopupMenu();
        JButton button = DropDownButtonFactory.createDropDownButton(
                new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)), menu);
        JMenuItem item = new JMenuItem(org.openide.awt.Actions.cutAmpersand((String) getValue("menuText")));
        item.setEnabled(isEnabled());

        addPropertyChangeListener(event -> {
            String propName = event.getPropertyName();
            if ("enabled".equals(propName)) {
                item.setEnabled((Boolean) event.getNewValue());
            } else if ("menuText".equals(propName)) {
                item.setText(org.openide.awt.Actions.cutAmpersand((String) event.getNewValue()));
            }
        });

        menu.add(item);
        item.addActionListener(LauncherAction.this);

        org.openide.awt.Actions.connect(button, this);
        menu.addPopupMenuListener(this);
        return button;
    }

    @Override
    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        JPopupMenu menu = (JPopupMenu) e.getSource();
        for (Component c : menu.getComponents())
            if (c instanceof JComponent && ((JComponent)c).getClientProperty("aaa") != null)
                menu.remove(c);

        List<LauncherActionItem> actionItems = LauncherActionHistory.getHistoryFor(command);
        if (!actionItems.isEmpty()) {
            JSeparator sep = new JSeparator();
            sep.putClientProperty("aaa", "aaa");
            menu.add(sep);
            for (var actionItem : actionItems) {
                JMenuItem item = new JMenuItem(actionItem.getDisplayName());
                item.putClientProperty("aaa", "aaa");
                menu.add(item);
                item.addActionListener(e1 -> RequestProcessor.getDefault().post(actionItem::repeatExecution));
            }
        }
    }

    @Override
    public void popupMenuWillBecomeInvisible(PopupMenuEvent e) { }

    @Override
    public void popupMenuCanceled(PopupMenuEvent e) { }
}
