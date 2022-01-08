package one.chartsy.ui.navigator;

import one.chartsy.SymbolGroupContent;
import one.chartsy.kernel.Kernel;
import one.chartsy.kernel.SymbolGroupHierarchy;
import one.chartsy.ui.swing.CheckBoxTreeDecorator;
import one.chartsy.ui.swing.CheckBoxTreeSelectionModel;
import one.chartsy.ui.swing.JTreeEnhancements;
import one.chartsy.ui.tree.TreeViewControl;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.explorer.view.BeanTreeView;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.text.DefaultEditorKit;
import javax.swing.tree.ExpandVetoException;

public class SymbolsTab extends TopComponent implements ExplorerManager.Provider {
    private final ExplorerManager explorerManager = new ExplorerManager();
    private final CheckBoxTreeSelectionModel selectionModel;
    private final ApplicationContext context;
    private TreeViewControl viewControl;
    private SymbolsViewListener viewControlAdapter;

    public SymbolsTab() {
        initComponents();
        setName(NbBundle.getMessage(SymbolsTab.class, "SymbolsTab.name"));
        setToolTipText(NbBundle.getMessage(SymbolsTab.class, "SymbolsTab.hint"));
        ActionMap map = getActionMap();
        // map.put("create category", ExplorerUtils.(explorerManager));
        map.put(DefaultEditorKit.copyAction, ExplorerUtils.actionCopy(explorerManager));
        map.put(DefaultEditorKit.cutAction, ExplorerUtils.actionCut(explorerManager));
        map.put(DefaultEditorKit.pasteAction, ExplorerUtils.actionPaste(explorerManager));
        //map.put("delete", Actions.actionDelete(explorerManager, true));
        associateLookup(ExplorerUtils.createLookup(explorerManager, map));
        
        // load root symbol group from the database
        BeanTreeView view = (BeanTreeView) scrollPane;
        context = Lookup.getDefault().lookup(Kernel.class).getApplicationContext();
        SymbolGroupHierarchy symbolHierarchy = context.getBean(SymbolGroupHierarchy.class);
        SymbolGroupContent rootContext = symbolHierarchy.getRootContext();
        if (rootContext != null) {
            SymbolGroupNode rootNode = SymbolGroupNode.create(rootContext, explorerManager, view, context);
            explorerManager.setRootContext(rootNode);
            viewControl = new TreeViewControl(rootNode);
            viewControlAdapter = new SymbolsViewListener(viewControl);
        }
        
        JTree tree = (JTree) scrollPane.getViewport().getView();
        view.expandNode(explorerManager.getRootContext());
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            
            @Override
            public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
                // do nothing
            }
            
            @Override
            public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
                if (event.getPath().getPathCount() == 1)
                    throw new ExpandVetoException(event);
            }
        });
        JTreeEnhancements.setSingleChildExpansionPolicy(tree);
        selectionModel = CheckBoxTreeDecorator.decorate(tree).getCheckBoxTreeSelectionModel();
    }
    
    public static SymbolsTab findComponent() {
        for (TopComponent comp : TopComponent.getRegistry().getOpened())
            if (comp instanceof SymbolsTab)
                return (SymbolsTab) comp;

        return null;
    }

    private JScrollPane scrollPane;
    
    private void initComponents() {
        scrollPane = new BeanTreeView();
        
        setLayout(new java.awt.BorderLayout());
        add(scrollPane, java.awt.BorderLayout.CENTER);
    }
    
    @Override
    public ExplorerManager getExplorerManager() {
        return explorerManager;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        ApplicationEventMulticaster eventMulticaster = context
                .getBean(AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME, ApplicationEventMulticaster.class);
        eventMulticaster.addApplicationListener(viewControlAdapter);
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        ApplicationEventMulticaster eventMulticaster = context
                .getBean(AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME, ApplicationEventMulticaster.class);
        eventMulticaster.removeApplicationListener(viewControlAdapter);
    }
}
