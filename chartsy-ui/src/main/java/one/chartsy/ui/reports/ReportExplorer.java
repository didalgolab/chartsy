package one.chartsy.ui.reports;

import one.chartsy.kernel.boot.FrontEnd;
import one.chartsy.kernel.Kernel;
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
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.text.DefaultEditorKit;
import javax.swing.tree.ExpandVetoException;
import java.awt.*;

@TopComponent.Description(preferredID = "ReportExplorer", iconBase = "one/chartsy/desktop/resources/reports (16 px).png", persistenceType = TopComponent.PERSISTENCE_ALWAYS)
@ServiceProvider(service = ReportExplorer.class)
public class ReportExplorer extends TopComponent implements ExplorerManager.Provider {
    private static final String TOP_REPORT_NAME = "Reports";
    private final ExplorerManager explorerManager = new ExplorerManager();
    private final CheckBoxTreeSelectionModel selectionModel;
    private final ApplicationContext context;
    private TreeViewControl viewControl;
    private ReportViewListener viewControlAdapter;
    private ReportHolder topReport = new ReportHolder(TOP_REPORT_NAME);
    private ReportGroupNode rootContext = new ReportGroupNode(topReport);

    public ReportExplorer() {
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
        
        // load root symbol group from the database
        BeanTreeView view = (BeanTreeView) scrollPane;
        FrontEnd frontEnd = Lookup.getDefault().lookup(FrontEnd.class);
        context = Kernel.getDefault().getApplicationContext();
        explorerManager.setRootContext(rootContext);
        viewControl = new TreeViewControl(rootContext);
        viewControlAdapter = new ReportViewListener(frontEnd, topReport, viewControl);

        JTree tree = (JTree) scrollPane.getViewport().getView();
        view.expandNode(explorerManager.getRootContext());
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

    public static ReportExplorer getDefault() {
        return Lookup.getDefault().lookup(ReportExplorer.class);
    }
}
