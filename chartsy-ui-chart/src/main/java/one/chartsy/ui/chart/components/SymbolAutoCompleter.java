/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import one.chartsy.Symbol;
import one.chartsy.core.text.SplittedString.Fragment;
import one.chartsy.core.text.StringSplitter;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.data.provider.SymbolProposalProvider;
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
    /** The splitter used to process entered multiple symbols in the search box. */
    private final StringSplitter splitter;

    /**
     * Constructs the {@code StockAutoCompleter} associated with the given text
     * {@code inputBox}.
     * 
     * @param inputBox
     *            the text input box
     */
    public SymbolAutoCompleter(JTextComponent inputBox, StringSplitter splitter) {
        super(inputBox);
        this.splitter = splitter;
    }

    /**
     * Gives a splitter used while processing multiple symbols entered in the search box.
     *
     * @return the string splitter currently in use
     */
    public StringSplitter getSplitter() {
        return splitter;
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

    public DataProvider getDataProvider() {
        return dataProvider;
    }

    public Optional<Fragment> getCurrentTextFragment() {
        return getSplitter().split(component.getText()).getFragmentAt(component.getCaretPosition());
    }

    @Override
    protected boolean updateListData() throws IOException, InterruptedException {
        Optional<Fragment> fragment = getCurrentTextFragment();
        if (fragment.isPresent()) {
            // retrieve symbol proposals from data provider

            SymbolProposalProvider proposalProvider = dataProvider.getLookup().lookup(SymbolProposalProvider.class);
            if (proposalProvider != null) {
                List<Symbol> symbols = proposalProvider.getProposals(fragment.get().toString());
                // update model
                if (symbols != null) {
                    int maxCount = Math.min(symbols.size(), getProperties().getMaximumRowCount());
                    List<Node> nodes = new ArrayList<>(maxCount);
                    for (int i = 0; i < maxCount; i++)
                        nodes.add(new Node(symbols.get(i)));
                    setListValues(nodes);
                }
            }
        } else {
            setListValues(List.of());
        }
        return true;
    }

    protected void setListValues(Collection<Node> values) {
        list.clear();
        list.addAll(values);
        table.tableChanged(new TableModelEvent(table.getModel(), TableModelEvent.HEADER_ROW));
    }

    @Override
    protected void acceptedListItem(Object selected) {
        if (selected instanceof Node) {
            var newContent = ((Node) selected).getName();

            Optional<Fragment> fragment = getCurrentTextFragment();
            if (fragment.isPresent())
                replaceText(fragment.get(), newContent);
            else
                component.setText(newContent);
            popupMenu.setVisible(false);
        }
    }

    protected void replaceText(Fragment fragment, String content) {
        replaceText(fragment.positionStart(), fragment.positionEnd(), content);
    }

    protected void replaceText(int p0, int p1, String content) {
        Document doc = component.getDocument();
        if (doc != null) {
            try {
                if (doc instanceof AbstractDocument)
                    ((AbstractDocument)doc).replace(p0, p1 - p0, content,null);
                else {
                    if (p0 != p1)
                        doc.remove(p0, p1 - p0);
                    if (content != null && content.length() > 0)
                        doc.insertString(p0, content, null);
                }
            } catch (BadLocationException e) {
                UIManager.getLookAndFeel().provideErrorFeedback(component);
            }
        }
    }

    @Override
    protected TableModel createTableModel(List<Node> values) {
        return new AbstractTableModel() {
            private final List<String> columns = List.of("name","description","exchange","lastPrice","lastPriceChange");

            @Override
            public int getRowCount() {
                return values.size();
            }

            @Override
            public int getColumnCount() {
                return columns.size();
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return switch (columnIndex) {
                    case 3,4 -> Double.class;
                    default -> String.class;
                };
            }

            @Override
            public Object getValueAt(int rowIndex, int columnIndex) {
                var sym = values.get(rowIndex).symbolInfo;
                return switch (columnIndex) {
                    case 0 -> sym.getName();
                    case 1 -> sym.getDisplayName();
                    case 2 -> sym.exchange();
                    case 3 -> sym.lastPrice();
                    case 4 -> sym.dailyChangePercentage();
                    default -> null;
                };
            }
        };
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
