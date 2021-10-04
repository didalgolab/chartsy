/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart.components;

import java.io.IOException;

import javax.swing.text.JTextComponent;

import one.chartsy.Symbol;
import one.chartsy.data.provider.DataProvider;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;

/**
 * Symbol search autocompleter.
 * 
 * @author Mariusz Bernacki
 * 
 */
public class StockAutoCompleter extends AutoCompleter<StockAutoCompleter.Node> {
    /** The data provider from which the suggestions are obtained. */
    private DataProvider dataProvider;
    
    /**
     * Constructs the {@code StockAutoCompleter} associated with the given text
     * {@code inputBox}.
     * 
     * @param inputBox
     *            the text input box
     */
    public StockAutoCompleter(JTextComponent inputBox) {
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
            if (true)
                throw new UnsupportedOperationException("TODO");
            /*List<GenericSymbol> symbols = dataProvider.getProposals(value);
            
            // update model
            if (symbols != null) {
                int maxCount = Math.min(symbols.size(), SysParams.Numeric.AUTOCOMPLETE_MAXIMUM_ROW_COUNT.getValue().intValue());
                Node[] nodes = new Node[maxCount];
                for (int i = 0; i < nodes.length; i++)
                    nodes[i] = new Node(symbols.get(i));
                list.setListData(nodes);
            }*/
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
    
    public static class Node extends AbstractNode {
        /** The symbol information data associated with this node. */
        private final Symbol symbolInfo;
        
        
        /**
         * Constructs a node from the provided symbol information.
         * 
         * @param symbolInfo
         *            the symbol information data
         */
        public Node(Symbol symbolInfo) {
            super(Children.LEAF);
            this.symbolInfo = symbolInfo;
            setName(symbolInfo.name());
        }
        
        protected void getLeft(StringBuilder out) {
            String displayName = symbolInfo.getDisplayName();
            if (displayName == null)
                displayName = "";
            
            out.append("<font color='#4e9a06'>")
            .append(displayName)
            .append("</font>");
        }
        
        protected void getRight(StringBuilder out) {
            String exchange = symbolInfo.getExchange();
            if (exchange == null)
                exchange = "";
            
            out.append("<font color='#000000'><b>")
            .append(symbolInfo.name())
            .append("</b></font>");
            if (!exchange.isEmpty())
                out.append(" <font color='#aaaaaa' size='-2'><i>@")
                .append(exchange)
                .append("</i></font>");
        }
        
        public @Override
        String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("<html>");
            getRight(sb);
            sb.append(" - ");
            getLeft(sb);
            sb.append("</html>");
            return sb.toString();
        }
    }
}
