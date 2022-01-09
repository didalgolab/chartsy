/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart.components;

import one.chartsy.Symbol;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.ui.chart.ChartFrame;
import one.chartsy.ui.chart.ChartHistoryEntry;
import one.chartsy.ui.chart.IconResource;
import one.chartsy.ui.chart.action.ButtonAction;
import one.chartsy.ui.chart.action.ChartActions;
import one.chartsy.ui.chart.internal.LongClickSupport;
import one.chartsy.ui.chart.internal.UpperCaseDocumentFilter;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;

/**
 * The symbol changer component
 * 
 * @author Mariusz Bernacki
 */
public class SymbolChanger extends JToolBar implements Serializable {
    
    private final ChartFrame chartFrame;
    private JTextField txtSymbol;
    private JButton btnSubmit;
    private JButton btnBack;
    private JButton btnForward;
    private DataProvider dataProvider;
    
    
    public SymbolChanger(ChartFrame frame) {
        super(JToolBar.HORIZONTAL);
        chartFrame = frame;
        setFloatable(false);
        setOpaque(false);
        setBorder(null);
        initComponents();
    }
    
    private void initComponents() {
        // symbol text field
        txtSymbol = new JTextField(11);
        ((AbstractDocument) txtSymbol.getDocument()).setDocumentFilter(new UpperCaseDocumentFilter());
        txtSymbol.setMargin(new Insets(0, 2, 0, 2));
        txtSymbol.setText(chartFrame.getChartData().getSymbol().name());
        Dimension d1 = new Dimension(90, 20);
        d1.height = Math.max(d1.height, txtSymbol.getPreferredSize().height);
        txtSymbol.setPreferredSize(d1);
        txtSymbol.setMinimumSize(d1);
        txtSymbol.setMaximumSize(d1);
        
        // submit button
        Insets margin = new Insets(0, 0, 0, 0);
        btnSubmit = new JButton(new ChangeStock());
        btnSubmit.setText("");
        btnSubmit.setBorderPainted(false);
        btnSubmit.setMargin(margin);
        
        // back button
        btnBack = new JButton(new BackAction()) {
            
            @Override
            public JPopupMenu getComponentPopupMenu() {
                List<ChartHistoryEntry> prevActions = chartFrame.getHistory().getPreviousActions();
                if (prevActions.isEmpty())
                    return null;
                
                JPopupMenu popup = new JPopupMenu();
                ListIterator<ChartHistoryEntry> iter = prevActions.listIterator(prevActions.size());
                int index = 0;
                while (iter.hasPrevious() && -index < 20)
                    popup.add(new GoToItemAction(iter.previous(), --index));
                
                popup.addSeparator();
                popup.add(new ClearListAction(prevActions));
                return popup;
            }
        };
        btnBack.setText("");
        btnBack.setBorderPainted(false);
        btnBack.setMargin(margin);
        LongClickSupport.decorate(btnBack).addListener(ChartActions.showComponentPopupMenuAction());
        
        // forward button
        btnForward = new JButton(new ForwardAction()) {
            
            @Override
            public JPopupMenu getComponentPopupMenu() {
                List<ChartHistoryEntry> nextActions = chartFrame.getHistory().getNextActions();
                if (nextActions.isEmpty())
                    return null;
                
                JPopupMenu popup = new JPopupMenu();
                Iterator<ChartHistoryEntry> iter = nextActions.iterator();
                int index = 0;
                while (iter.hasNext() && index < 20)
                    popup.add(new GoToItemAction(iter.next(), ++index));
                
                popup.addSeparator();
                popup.add(new ClearListAction(nextActions));
                return popup;
            }
        };
        btnForward.setText("");
        btnForward.setBorderPainted(false);
        btnForward.setMargin(margin);
        LongClickSupport.decorate(btnForward).addListener(ChartActions.showComponentPopupMenuAction());
        
        if (!chartFrame.getHistory().hasBackHistory()) {
            btnBack.setEnabled(false);
        }
        if (!chartFrame.getHistory().hasFwdHistory()) {
            btnForward.setEnabled(false);
        }
        
        add(btnBack);
        add(txtSymbol);
        add(btnSubmit);
        add(btnForward);
        
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "back");
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "next");
        getActionMap().put("back", new ButtonAction(btnBack));
        getActionMap().put("next", new ButtonAction(btnForward));
        
        dataProvider = chartFrame.getChartData().getDataProvider();
        SymbolAutoCompleter completer = new SymbolAutoCompleter(txtSymbol);
        completer.setDataProvider(dataProvider);
    }
    
    public JButton getBackButton() {
        return btnBack;
    }
    
    public JButton getForwardButton() {
        return btnForward;
    }
    
    public Action submit = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
            btnSubmit.doClick();
        }
    };
    
    public void updateToolbar() {
        removeAll();
        initComponents();
        if (getParent() instanceof ChartToolbar)
            ((ChartToolbar) getParent()).applyVisualStyle(this);
        validate();
        repaint();
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        btnBack.setEnabled(enabled);
        btnForward.setEnabled(enabled);
        btnSubmit.setEnabled(enabled);
    }
    
    private void notifyError(Throwable t) {
        NotifyDescriptor descriptor = new NotifyDescriptor.Message(
                t.getMessage(), NotifyDescriptor.ERROR_MESSAGE);
        DialogDisplayer.getDefault().notify(descriptor);
    }
    
    private static abstract class SymbolChangerAction extends AbstractAction {
        
        public SymbolChangerAction(String name, String tooltip, String icon) {
            putValue(NAME, name);
            putValue(SHORT_DESCRIPTION, tooltip);
            if (icon != null) {
                putValue(SMALL_ICON, IconResource.getIcon(icon/*, 16*/));
                putValue(LARGE_ICON_KEY, IconResource.getIcon(icon/*, 32*/));
            }
        }
    }
    
    public class ChangeStock extends SymbolChangerAction {
        
        public ChangeStock() {
            super("Submit", "Submit Symbol", "one/chartsy/ui/chart/icons/accept.png");
            // remove large icon from the action
            putValue(LARGE_ICON_KEY, null);
        }
        
        @Override
        public void actionPerformed(ActionEvent event) {
            setEnabled(false);
            
            try {
                SwingUtilities.invokeLater(() -> {
                    setEnabled(true);
                    final String ticker = txtSymbol.getText().trim();
                    chartFrame.symbolChanged(new Symbol(ticker, dataProvider));
                });
            } catch (Exception e) {
                notifyError(e);
            }
        }
    }
    
    /**
     * The action that allows a user to go to a previous chart. The <i>previous
     * chart</i> is either a previously opened chart taken from the history or a
     * previous item from the chart slide show list (depending on a
     * {@code ChartActionNavigator} currently installed on the chart frame.
     * 
     * @author Mariusz Bernacki
     * 
     */
    public class BackAction extends SymbolChangerAction {

        public BackAction() {
            super("Go Back", "Click to go back, hold to see history", "one/chartsy/ui/chart/icons/go-back");
        }
        
        @Override
        public void actionPerformed(ActionEvent e) {
            ChartHistoryEntry action = chartFrame.getHistory().go(-1);
            if (action != null)
                chartFrame.navigationChange(action);
            
            updateToolbar();
        }
    }
    
    /**
     * The action that allows a user to go to a next chart. The <i>next
     * chart</i> is either a previously opened chart taken from the forward
     * history or a next item from the chart slide show list (depending on a
     * {@code ChartActionNavigator} currently installed on the chart frame.
     * 
     * @author Mariusz Bernacki
     * 
     */
    public class ForwardAction extends SymbolChangerAction {

        public ForwardAction() {
            super("Go Forward", "Go Forward", "one/chartsy/ui/chart/icons/go-forward.png");
        }
        
        @Override
        public void actionPerformed(ActionEvent e) {
            ChartHistoryEntry item = chartFrame.getHistory().go(1);
            if (item != null)
                chartFrame.navigationChange(item);
            
            updateToolbar();
        }
    }
    
    public class GoToItemAction extends SymbolChangerAction {
        /** The navigation step relative to the current position. */
        private final int step;
        
        
        public GoToItemAction(ChartHistoryEntry action, int step) {
            this((String) action.getValue(Action.NAME), step);
        }
        
        public GoToItemAction(String name, int step) {
            super(name, name, null);
            this.step = step;
        }
        
        @Override
        public void actionPerformed(ActionEvent e) {
            ChartHistoryEntry item = chartFrame.getHistory().go(step);
            if (item != null)
                chartFrame.navigationChange(item);
        }
    }
    
    public class ClearListAction extends SymbolChangerAction {
        /** The list to be cleared as a result of this action invocation. */
        private final List<ChartHistoryEntry> list;
        
        
        public ClearListAction(List<ChartHistoryEntry> list) {
            super("Clear", "Clear", null);
            this.list = Objects.requireNonNull(list);
        }
        
        @Override
        public void actionPerformed(ActionEvent e) {
            list.clear();
            updateToolbar();
        }
    }
}
