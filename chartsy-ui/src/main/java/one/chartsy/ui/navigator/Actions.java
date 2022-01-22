package one.chartsy.ui.navigator;

import one.chartsy.Symbol;
import one.chartsy.SymbolGroupContent;
import one.chartsy.SymbolIdentity;
import one.chartsy.TimeFrame;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.persistence.domain.SymbolGroupAggregateData;
import one.chartsy.persistence.domain.model.SymbolGroupRepository;
import one.chartsy.ui.ChartTopComponent;
import one.chartsy.ui.FrontEnd;
import one.chartsy.ui.chart.*;
import one.chartsy.ui.nodes.BaseNodeAction;
import one.chartsy.ui.nodes.NodeSelection;
import org.apache.logging.log4j.LogManager;
import org.openide.DialogDisplayer;
import org.openide.ErrorManager;
import org.openide.NotifyDescriptor;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.actions.Presenter;
import org.springframework.expression.ExpressionParser;

import javax.swing.*;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

public abstract class Actions {

    /**
     * The action responsible for adding new symbol group to the symbols
     * navigator hierarchy view.
     */
    public static class AddSymbolGroup extends BaseNodeAction implements Presenter.Popup {

        @Override
        protected void performAction(Node[] parents) {
            // label for Add Symbol Group dialog title
            String label = NbBundle.getMessage(getClass(), "NewTypes.SymbolGroup");

            // ask user for new symbol group name
            NotifyDescriptor.InputLine msg = new NotifyDescriptor.InputLine(label, getName());
            if (DialogDisplayer.getDefault().notify(msg) == NotifyDescriptor.OK_OPTION) {
                String name = msg.getInputText();
                if (name.isEmpty())
                    return;

                // create child symbol group for each parent
                for (Node parent : parents) {
                    SymbolGroupAggregateData group = new SymbolGroupAggregateData();
                    group.setName(name);
                    group.setContentType(SymbolGroupContent.Type.FOLDER);
                    group.setParentGroupId(asSymbolGroup(parent).getId());

                    SymbolGroupRepository repo = getApplicationContext(parent).map(ctx -> ctx.getBean(SymbolGroupRepository.class)).orElseThrow();
                    repo.saveAndFlush(group);
                }
                NodeSelection.selectAllAdded(parents);
            }
        }

        @Override
        protected boolean enable(Node[] nodes) {
            for (Node node : nodes) {
                SymbolGroupContent groupData = asSymbolGroup(node);
                if (groupData == null)
                    return false;
            }
            return (nodes.length > 0);
        }

        //@Override
        //protected String iconResource() {
        //    return RESOURCES + "symbol-group (16 px).png";
        //}

        @Override
        public JMenuItem getPopupPresenter() {
            return new JMenuItem(this);
        }
    }

    public static class AddDataProviderFromDescriptor extends BaseNodeAction {

        @Override
        protected void performAction(Node[] parents) {
            // label for Add Data Provider From Descriptor dialog title
            String label = NbBundle.getMessage(getClass(), "DataProvider.descriptor");

            // ask user for new symbol group name
            NotifyDescriptor.InputLine msg = new NotifyDescriptor.InputLine(label, getName());
            if (DialogDisplayer.getDefault().notify(msg) == NotifyDescriptor.OK_OPTION) {
                String expr = msg.getInputText();
                expr = cleanupDescriptor(expr);
                if (expr.isEmpty())
                    return;

                // create child symbol group for each parent
                for (Node parent : parents) {
                    var appContext = getApplicationContext(parent).orElseThrow();
                    var exprParser = appContext.getBean(ExpressionParser.class);
                    var dataProvider = exprParser.parseExpression(expr).getValue(DataProvider.class);
                    try {
                        var symbolGroup = new SymbolGroupAggregateData();
                        symbolGroup.setName(dataProvider.getName());
                        symbolGroup.setContentType(SymbolGroupContent.Type.DATA_PROVIDER);
                        symbolGroup.setParentGroupId(asSymbolGroup(parent).getId());
                        symbolGroup.setDataProviderDescriptor(expr);

                        appContext.getBean(SymbolGroupRepository.class).saveAndFlush(symbolGroup);
                    } finally {
                        if (dataProvider instanceof AutoCloseable c) {
                            try {
                                c.close();
                            } catch (Exception e) {
                                LogManager.getLogger(getClass()).warn("Error closing {}", dataProvider.getName(), e);
                            }
                        }
                    }
                }
                NodeSelection.selectAllAdded(parents);
            }
        }

        @Override
        protected boolean enable(Node[] nodes) {
            for (Node node : nodes) {
                SymbolGroupContent groupData = asSymbolGroup(node);
                if (groupData == null)
                    return false;
            }
            return (nodes.length > 0);
        }

        private static String cleanupDescriptor(String expr) {
            expr = expr.strip();
            if (expr.startsWith("\"") && expr.endsWith("\"") && expr.length() >= 2)
                expr = expr.substring(1, expr.length() - 1);

            return expr;
        }
    }

    /**
     * The action responsible for symbol or symbol group nodes deletion together
     * with updating the tree view and saving changes to the underlying model.
     *
     */
    public static class Delete extends BaseNodeAction {

        @Override
        protected boolean asynchronous() {
            return true;
        }

        @Override
        protected boolean enable(Node[] nodes) {
            for (Node node : nodes)
                if (!node.canDestroy())
                    return false;
            return true;
        }

        @Override
        protected void performAction(Node[] nodes) {
            if (confirmDelete(nodes))
                performDelete(nodes);
        }
    }

    /**
     * The action opens multiple selected symbols in a single chart frame.
     *
     * @author Mariusz Bernacki
     *
     */
    public static class OpenSymbolChart extends BaseNodeAction {

        @Override
        protected void performAction(List<Node> nodes) {
            List<Symbol> symbolList = new ArrayList<>();
            for (Node node : nodes) {
                Symbol symbol = node.getLookup().lookup(Symbol.class);
                if (symbol != null)
                    symbolList.add(symbol);
            }

            if (!symbolList.isEmpty())
                openChart(symbolList);
        }

        @Override
        protected boolean enable(Node[] nodes) {
            for (Node node : nodes)
                if (!(node instanceof SymbolNode))
                    return false;
            return true;
        }
    }

    /**
     * The action opens multiple selected symbols in a single chart frame.
     *
     * @author Mariusz Bernacki
     *
     */
    public static class ChartAllInFolder extends BaseNodeAction {

        @Override
        protected void performAction(List<Node> nodes) {
            Set<Symbol> symbolList = new HashSet<>();
            LinkedList<Node> worklist = new LinkedList<>(nodes);
            worklist.replaceAll(node -> {
                Symbol symbol = node.getLookup().lookup(Symbol.class);
                return (symbol != null) ? node.getParentNode() : node;
            });
            while (!worklist.isEmpty()) {
                Node node = worklist.removeFirst();

                SymbolGroupContent symbolGroup = node.getLookup().lookup(SymbolGroupContent.class);
                if (symbolGroup != null)
                    worklist.addAll(Arrays.asList(node.getChildren().getNodes()));

                Symbol symbol = node.getLookup().lookup(Symbol.class);
                if (symbol != null)
                    symbolList.add(symbol);
            }

            if (!symbolList.isEmpty()) {
                var sortedList = new ArrayList<>(symbolList);
                sortedList.sort(SymbolIdentity.comparator());
                openChart(sortedList);
            }
        }

        @Override
        protected boolean enable(Node[] nodes) {
            return nodes.length > 0;
        }
    }

    public static SymbolGroupContent asSymbolGroup(Node node) {
        return node.getLookup().lookup(SymbolGroupContent.class);
    }

    private static boolean confirmDelete(Node[] sel) {
        String message, title;
        if (sel.length == 1) {
            message = NbBundle.getMessage(Actions.class, "Delete.confirm.msg", sel[0].getDisplayName());
            title = NbBundle.getMessage(Actions.class, "Delete.confirm");
        } else {
            message = NbBundle.getMessage(Actions.class, "Delete.confirm.many.msg", sel.length);
            title = NbBundle.getMessage(Actions.class, "Delete.confirm.many");
        }
        NotifyDescriptor desc = new NotifyDescriptor.Confirmation(message, title, NotifyDescriptor.YES_NO_OPTION);
        return NotifyDescriptor.YES_OPTION.equals(DialogDisplayer.getDefault().notify(desc));
    }

    protected static void performDelete(Node[] nodes) {
        if (nodes.length == 1) {
            try {
                nodes[0].destroy();
            } catch (IOException e) {
                ErrorManager.getDefault().notify(e);
            }
        } else {
            Map<Node, List<Node>> map = new HashMap<>();
            for (Node node : nodes)
                map.computeIfAbsent(node.getParentNode(), __ -> new ArrayList<>()).add(node);

            for (Map.Entry<Node, List<Node>> e : map.entrySet()) {
                /*if (e.getKey() instanceof SymbolGroupDataProviderFolderNode && ((SymbolGroupDataProviderFolderNode) e.getKey()).getDataProvider() instanceof LocalDataProvider) {
                    List<SymbolExt> symbols = new ArrayList<>(e.getValue().size());
                    for (Node node : e.getValue())
                        symbols.add(((SymbolNode) node).getSymbol());
                    RemoveSymbol.performAction((LocalDataProvider) ((SymbolGroupDataProviderFolderNode) e.getKey()).getDataProvider(), symbols);
                } else if (e.getKey() instanceof SymbolGroupLocalDataProviderNode) {
                    List<SymbolExt> symbols = new ArrayList<>(e.getValue().size());
                    for (Node node : e.getValue())
                        symbols.add(((SymbolNode) node).getSymbol());
                    RemoveSymbol.performAction(((SymbolGroupLocalDataProviderNode) e.getKey()).getDataProvider(), symbols);
                } else*/ if (e.getKey() instanceof SymbolGroupNode) {
                    Stream<SymbolGroupAggregateData> toDelete = e.getValue().stream()
                            .filter(SymbolGroupNode.class::isInstance)
                            .map(SymbolGroupNode.class::cast)
                            .map(SymbolGroupNode::getEntity)
                            .map(SymbolGroupAggregateData.class::cast);
                    Node parentNode = e.getKey();
                    BaseNodeAction.getApplicationContext(parentNode).orElseThrow().getBean(SymbolGroupRepository.class)
                            .deleteAll(toDelete::iterator);
                }
            }
        }
    }

    public static void openChart(List<Symbol> symbolList) {
        openChart(symbolList, TimeFrame.Period.DAILY);
    }

    public static void openChart(List<Symbol> symbolList, TimeFrame timeFrame) {
        FrontEnd frontEnd = Lookup.getDefault().lookup(FrontEnd.class);
        ChartTemplate template = frontEnd.getApplicationContext().getBean(ChartTemplate.class);

        Symbol first = symbolList.get(0);
        ChartData chartData = new ChartData();
        chartData.setSymbol(first);
        chartData.setDataProvider(first.getProvider());
        chartData.setTimeFrame(timeFrame);
        chartData.setChart(frontEnd.getApplicationContext().getBean("Candle Stick", Chart.class));

        ChartFrame chartFrame = new ChartFrame();
        chartFrame.setChartData(chartData);
        chartFrame.setChartTemplate(template);
        if (symbolList.size() > 1) {
            //symbolList.remove(0); // remove the first/current symbol from the stack
            chartFrame.setHistory(new ChartHistory(symbolList, timeFrame));
        }
        ChartTopComponent chartTC = new ChartTopComponent(chartFrame);
        chartTC.open();
        chartTC.requestActive();
    }
}
