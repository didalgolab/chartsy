/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.navigator;

import one.chartsy.Symbol;
import one.chartsy.SymbolGroupContent;
import one.chartsy.SymbolGroupContentRepository;
import one.chartsy.data.provider.DataProviderLoader;
import one.chartsy.kernel.StartupMetrics;
import one.chartsy.kernel.SymbolGroupHierarchy;
import one.chartsy.ui.StartupServices;
import one.chartsy.ui.actions.CollapseAllAction;
import one.chartsy.ui.actions.ExpandAllAction;
import one.chartsy.ui.swing.CheckBoxTreeDecorator;
import one.chartsy.ui.swing.CheckBoxTreeSelectionModel;
import one.chartsy.ui.swing.JTreeEnhancements;
import one.chartsy.ui.tree.TreeViewControl;
import org.openide.awt.ToolbarWithOverflow;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.explorer.view.BeanTreeView;
import org.openide.explorer.view.Visualizer;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
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
import java.awt.*;
import java.util.concurrent.CompletionException;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SymbolsTab extends TopComponent implements ExplorerManager.Provider {
    private static final Logger LOG = Logger.getLogger(SymbolsTab.class.getName());
    private final ExplorerManager explorerManager = new ExplorerManager();
    private final CheckBoxTreeSelectionModel selectionModel;
    private volatile ApplicationContext context;
    private TreeViewControl viewControl;
    private SymbolsViewListener viewControlAdapter;
    private boolean listenerRegistered;

    public SymbolsTab() {
        StartupMetrics.mark("symbolsTab:start");
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
        explorerManager.setRootContext(createStatusNode("Loading..."));
        loadRootContext(view);
        
        JTree tree = (JTree) scrollPane.getViewport().getView();
        toolbar.putClientProperty(JTree.class, tree);
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
        setForeground(Color.yellow);
        StartupMetrics.mark("symbolsTab:ready");
    }

    public JTree getTree() {
        return (JTree) scrollPane.getViewport().getView();
    }

    public static SymbolsTab findComponent() {
        for (TopComponent comp : TopComponent.getRegistry().getOpened())
            if (comp instanceof SymbolsTab)
                return (SymbolsTab) comp;

        return null;
    }

    private JScrollPane scrollPane;
    private JToolBar toolbar;

    public JToolBar getToolbar() {
        return toolbar;
    }

    protected void initComponents() {
        scrollPane = new BeanTreeView();

        var toolbar = new ToolbarWithOverflow();
        toolbar.setDisplayOverflowOnHover(true);
        toolbar.putClientProperty("PreferredIconSize", 24);
        toolbar.add(new ExpandAllAction());
        toolbar.add(new CollapseAllAction());
        this.toolbar = toolbar;

        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
        add(toolbar, BorderLayout.NORTH);
    }
    
    @Override
    public ExplorerManager getExplorerManager() {
        return explorerManager;
    }

    public TreeSelectionModel getSelectionModel() {
        return selectionModel;
    }

    public List<Symbol> selectedSymbols() {
        var paths = getSelectionModel().getSelectionPaths();
        if (paths == null || paths.length == 0)
            return List.of();
        return getSymbolsAtPaths(paths);
    }

    public List<Symbol> getSymbolsAtPaths(TreePath... paths) {
        if (context == null)
            return List.of();
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
        registerViewListener();
    }

    @Override
    public void removeNotify() {
        unregisterViewListener();
        super.removeNotify();
    }

    private void loadRootContext(BeanTreeView view) {
        StartupServices.symbols().thenApplyAsync(applicationContext -> {
            StartupMetrics.mark("symbolsTab:rootContext:start");
            var symbolHierarchy = applicationContext.getBean(SymbolGroupHierarchy.class);
            var snapshot = new SymbolsLoadingSnapshot(applicationContext, symbolHierarchy.getRootContext());
            StartupMetrics.mark("symbolsTab:rootContext:ready");
            return snapshot;
        }).whenComplete((snapshot, error) -> EventQueue.invokeLater(() -> {
            if (error != null) {
                showLoadingFailure(error);
                return;
            }
            applyLoadedRoot(view, snapshot);
        }));
    }

    private void applyLoadedRoot(BeanTreeView view, SymbolsLoadingSnapshot snapshot) {
        context = snapshot.context();
        SymbolGroupContent rootContext = snapshot.rootContext();
        if (rootContext != null) {
            StartupMetrics.mark("symbolsTab:nodeBuild:start");
            SymbolGroupNode rootNode = SymbolGroupNode.create(rootContext, explorerManager, view, context);
            StartupMetrics.mark("symbolsTab:nodeBuild:ready");
            explorerManager.setRootContext(rootNode);
            viewControl = new TreeViewControl(rootNode);
            viewControlAdapter = new SymbolsViewListener(viewControl);
            registerViewListener();
            view.expandNode(rootNode);
            StartupMetrics.mark("symbolsTab:dataReady");
        } else {
            explorerManager.setRootContext(createStatusNode("No symbols"));
        }
    }

    private void showLoadingFailure(Throwable error) {
        Throwable cause = (error instanceof CompletionException && error.getCause() != null) ? error.getCause() : error;
        explorerManager.setRootContext(createStatusNode("Failed to load symbols"));
        StartupMetrics.mark("symbolsTab:failed");
        LOG.log(Level.SEVERE, "Unable to load symbol hierarchy", cause);
    }

    private void registerViewListener() {
        if (listenerRegistered || !isDisplayable() || context == null || viewControlAdapter == null)
            return;
        ApplicationEventMulticaster eventMulticaster = context
                .getBean(AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME, ApplicationEventMulticaster.class);
        eventMulticaster.addApplicationListener(viewControlAdapter);
        listenerRegistered = true;
    }

    private void unregisterViewListener() {
        if (!listenerRegistered || context == null || viewControlAdapter == null)
            return;
        ApplicationEventMulticaster eventMulticaster = context
                .getBean(AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME, ApplicationEventMulticaster.class);
        eventMulticaster.removeApplicationListener(viewControlAdapter);
        listenerRegistered = false;
    }

    private Node createStatusNode(String message) {
        var node = new AbstractNode(Children.LEAF);
        node.setDisplayName(message);
        return node;
    }

    private record SymbolsLoadingSnapshot(ApplicationContext context, SymbolGroupContent rootContext) {
    }
}
