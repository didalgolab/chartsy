/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart.components;

import java.io.IOException;
import java.util.List;

import javax.swing.text.JTextComponent;

import one.chartsy.Symbol;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.data.provider.SymbolProposalProvider;
import one.chartsy.ui.chart.SysParams;
import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;

/**
 * Symbol search autocompleter.
 * 
 * @author Mariusz Bernacki
 * 
 */
public class SymbolAutoCompleter extends AutoCompleter<SymbolAutoCompleter.Node> {
    /** The data provider from which the suggestions are obtained. */
    private DataProvider dataProvider;
    
    /**
     * Constructs the {@code StockAutoCompleter} associated with the given text
     * {@code inputBox}.
     * 
     * @param inputBox
     *            the text input box
     */
    public SymbolAutoCompleter(JTextComponent inputBox) {
        super(inputBox);
    }
    
    /**
     * Changes suggestions data provider associated with the autocompleter.
     * 
     * @param dataProvider
     *            the new suggestions data provider
     */
    public void setDataProvider(DataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }
    
    @Override
    protected boolean updateListData() throws IOException {
        String value = component.getText();
        if (!value.isEmpty()) {
            // retrieve symbol proposals from data provider

            SymbolProposalProvider proposalProvider = dataProvider.getLookup().lookup(SymbolProposalProvider.class);
            if (proposalProvider != null) {
                List<Symbol> symbols = proposalProvider.getProposals(value);
                // update model
                if (symbols != null) {
                    int maxCount = Math.min(symbols.size(), SysParams.AUTOCOMPLETE_MAXIMUM_ROW_COUNT.intValue());
                    Node[] nodes = new Node[maxCount];
                    for (int i = 0; i < nodes.length; i++)
                        nodes[i] = new Node(symbols.get(i));
                    list.setListData(nodes);
                }
            }
        } else {
            list.setListData(new Node[0]);
        }
        return true;
    }
    
    @Override
    protected void acceptedListItem(Object selected) {
        if (selected instanceof Node) {
            component.setText(((Node) selected).getName());
            popupMenu.setVisible(false);
        }
    }
    
    public static class Node extends AbstractNode implements SelectionStateTransformable<Node> {
        /** The symbol information data associated with this node. */
        private final Symbol symbolInfo;
        /** The flag indicating if the node is currently selected. */
        private final boolean selected;
        
        
        /**
         * Constructs a node from the provided symbol information.
         * 
         * @param symbolInfo
         *            the symbol information data
         */
        public Node(Symbol symbolInfo) {
            this(symbolInfo, false);
        }

        protected Node(Symbol symbolInfo, boolean selected) {
            super(Children.LEAF);
            this.symbolInfo = symbolInfo;
            this.selected = selected;
            setName(symbolInfo.name());
        }

        @Override
        public Node getAsSelected() {
            if (selected)
                return this;
            return new Node(symbolInfo, true);
        }

        protected void getRight(StringBuilder out) {
            String displayName = symbolInfo.getDisplayName();
            if (StringUtils.isEmpty(displayName))
                return;

            out.append(" - ");
            if (!selected)
                out.append("<font color='#4e9a06'>");
            out.append(displayName);
            if (!selected)
                out.append("</font>");
        }
        
        protected void getLeft(StringBuilder out) {
            String exchange = symbolInfo.getExchange();
            if (exchange == null)
                exchange = "";
            
            //out.append("<font color='#000000'><b>");
            out.append("<b>");
            out.append(symbolInfo.name());
            //out.append("</b></font>");
            out.append("</b>");
            if (!exchange.isEmpty()) {
                out.append(selected? " <font size='-2'>": " <font color='#aaaaaa' size='-2'>");
                out.append("<i>@");
            }
            out.append(exchange);
            out.append("</i></font>");
        }
        
        public @Override
        String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("<html>");
            getLeft(sb);
            getRight(sb);
            sb.append("</html>");
            return sb.toString();
        }
    }
}
