/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.reports;

import one.chartsy.kernel.boot.FrontEnd;
import one.chartsy.kernel.Kernel;
import one.chartsy.kernel.StartupMetrics;
import one.chartsy.ui.StartupServices;
import one.chartsy.ui.swing.CheckBoxTreeDecorator;
import one.chartsy.ui.swing.CheckBoxTreeSelectionModel;
import one.chartsy.ui.swing.JTreeEnhancements;
import one.chartsy.ui.tree.TreeViewControl;
import org.openide.awt.ToolbarWithOverflow;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.explorer.view.BeanTreeView;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.TopComponent;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.text.DefaultEditorKit;
import javax.swing.tree.ExpandVetoException;
import java.awt.*;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

@TopComponent.Description(preferredID = "ReportExplorer", iconBase = "one/chartsy/desktop/resources/reports (16 px).png", persistenceType = TopComponent.PERSISTENCE_ALWAYS)
@ServiceProvider(service = ReportExplorer.class)
public class ReportExplorer extends TopComponent implements ExplorerManager.Provider {
    private static final String TOP_REPORT_NAME = "Reports";
    private static final Logger LOG = Logger.getLogger(ReportExplorer.class.getName());
    private final ExplorerManager explorerManager = new ExplorerManager();
    private final CheckBoxTreeSelectionModel selectionModel;
    private volatile ApplicationContext context;
    private volatile FrontEnd frontEnd;
    private TreeViewControl viewControl;
    private ReportViewListener viewControlAdapter;
    private boolean listenerRegistered;
    private final ReportHolder topReport = new ReportHolder(TOP_REPORT_NAME);

    public ReportExplorer() {
        StartupMetrics.mark("reportExplorer:start");
        initComponents();
        setName(NbBundle.getMessage(ReportExplorer.class, "ReportExplorer.name"));
        setToolTipText(NbBundle.getMessage(ReportExplorer.class, "ReportExplorer.hint"));
        ActionMap map = getActionMap();
        // map.put("create category", ExplorerUtils.(explorerManager));
        map.put(DefaultEditorKit.copyAction, ExplorerUtils.actionCopy(explorerManager));
        map.put(DefaultEditorKit.cutAction, ExplorerUtils.actionCut(explorerManager));
        map.put(DefaultEditorKit.pasteAction, ExplorerUtils.actionPaste(explorerManager));
        //map.put("delete", Actions.actionDelete(explorerManager, true));
        associateLookup(ExplorerUtils.createLookup(explorerManager, map));

        BeanTreeView view = (BeanTreeView) scrollPane;
        explorerManager.setRootContext(createStatusNode("Loading..."));
        loadRootContext(view);

        JTree tree = (JTree) scrollPane.getViewport().getView();
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {

            @Override
            public void treeWillExpand(TreeExpansionEvent event) {
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
        StartupMetrics.mark("reportExplorer:ready");
    }
    
    public static ReportExplorer findComponent() {
        for (TopComponent comp : TopComponent.getRegistry().getOpened())
            if (comp instanceof ReportExplorer)
                return (ReportExplorer) comp;

        return null;
    }

    private JScrollPane scrollPane;
    private ToolbarWithOverflow toolbar;

    @Override
    public Action[] getActions() {
        return super.getActions();
    }

    private void initComponents() {
        scrollPane = new BeanTreeView();
        toolbar = new ToolbarWithOverflow();
        toolbar.setDisplayOverflowOnHover(true);
        toolbar.setFloatable(false);
        toolbar.add(new JLabel("Placeholder 1"));
        toolbar.add(new JLabel("Placeholder 2"));
        toolbar.add(new JLabel("Placeholder 3"));
        toolbar.add(new JLabel("Placeholder 4"));

        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
        add(toolbar, BorderLayout.NORTH);
    }
    
    @Override
    public ExplorerManager getExplorerManager() {
        return explorerManager;
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
        StartupServices.frontEnd()
                .thenApply(frontEnd -> new ReportLoadingSnapshot(frontEnd, Kernel.getDefault().getApplicationContext()))
                .whenComplete((snapshot, error) -> EventQueue.invokeLater(() -> {
                    if (error != null) {
                        showLoadingFailure(error);
                        return;
                    }
                    applyLoadedRoot(view, snapshot);
                }));
    }

    private void applyLoadedRoot(BeanTreeView view, ReportLoadingSnapshot snapshot) {
        context = snapshot.context();
        frontEnd = snapshot.frontEnd();
        var rootContext = new ReportGroupNode(topReport);
        explorerManager.setRootContext(rootContext);
        viewControl = new TreeViewControl(rootContext);
        viewControlAdapter = new ReportViewListener(frontEnd, topReport, viewControl);
        registerViewListener();
        view.expandNode(rootContext);
        StartupMetrics.mark("reportExplorer:dataReady");
    }

    private void showLoadingFailure(Throwable error) {
        Throwable cause = (error instanceof CompletionException && error.getCause() != null) ? error.getCause() : error;
        explorerManager.setRootContext(createStatusNode("Failed to load reports"));
        StartupMetrics.mark("reportExplorer:failed");
        LOG.log(Level.SEVERE, "Unable to load reports explorer", cause);
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

    public static ReportExplorer getDefault() {
        return Lookup.getDefault().lookup(ReportExplorer.class);
    }

    private record ReportLoadingSnapshot(FrontEnd frontEnd, ApplicationContext context) {
    }
}
