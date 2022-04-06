package one.chartsy.ui.navigator;

import one.chartsy.Symbol;
import one.chartsy.SymbolGroupContent;
import one.chartsy.SymbolGroupContentRepository;
import one.chartsy.data.provider.DataProviderLoader;
import one.chartsy.kernel.Kernel;
import one.chartsy.kernel.SymbolGroupHierarchy;
import one.chartsy.ui.swing.CheckBoxTreeDecorator;
import one.chartsy.ui.swing.CheckBoxTreeSelectionModel;
import one.chartsy.ui.swing.JTreeEnhancements;
import one.chartsy.ui.tree.TreeViewControl;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.explorer.view.BeanTreeView;
import org.openide.explorer.view.Visualizer;
import org.openide.nodes.Node;
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
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.util.*;

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
        context = Kernel.getDefault().getApplicationContext();
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

    public TreeSelectionModel getSelectionModel() {
        return selectionModel;
    }

    public List<Symbol> getSelectedSymbols() {
        var paths = getSelectionModel().getSelectionPaths();
        if (paths.length == 0)
            return List.of();
        return getSymbolsAtPaths(paths);
    }

    public List<Symbol> getSymbolsAtPaths(TreePath... paths) {
        var universe = new LinkedHashSet<Symbol>();
        var worklist = new ArrayDeque<SymbolGroupContent>(paths.length);
        for (TreePath path : paths) {
            // convert path to node
            Node node = Visualizer.findNode(path.getLastPathComponent());

            // check if node associates a symbol
            var symbol = node.getLookup().lookup(Symbol.class);
            if (symbol != null)
                universe.add(symbol);

            // check if node associates a symbol group
            SymbolGroupContent group = node.getLookup().lookup(SymbolGroupContent.class);
            if (group != null)
                worklist.add(group);

        }

        // flatten out symbol group's worklist
        var repository = context.getBean(SymbolGroupContentRepository.class);
        var dataLoader = context.getBean(DataProviderLoader.class);
        while (!worklist.isEmpty()) {
            SymbolGroupContent group = worklist.remove();
            for (SymbolGroupContent child : group.getContent(repository, dataLoader)) {
                child.getAsSymbol().ifPresent(universe::add);
                worklist.add(child);
            }
        }
        return new ArrayList<>(universe);
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
