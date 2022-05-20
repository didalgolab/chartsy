/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.navigator;

import javax.swing.Action;

import one.chartsy.Symbol;
import one.chartsy.SymbolGroupContent;
import one.chartsy.ui.nodes.NodeActions;
import org.openide.actions.CopyAction;
import org.openide.actions.CutAction;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.util.Lookup;
import org.openide.util.actions.SystemAction;
import org.openide.util.lookup.Lookups;

public class SymbolNode extends AbstractNode {

    private static final String SYMBOL_ICON_BASE = "one/chartsy/ui/navigator/symbol-2.png";

    private Symbol symbol;

    public SymbolNode(Symbol symbol) {
        super(Children.LEAF, currentLookup(symbol));
        setName(symbol.getName());
        setIconBaseWithExtension(SYMBOL_ICON_BASE);
        this.symbol = symbol;
    }
    
    private static Lookup currentLookup(Symbol symbol) {
        return Lookups.singleton(symbol);
    }
    
    public Symbol getSymbol() {
        return symbol;
    }

    public SymbolGroupContent getParentGroup() {
        return getParentNode().getLookup().lookup(SymbolGroupContent.class);
    }

    @Override
    public boolean canCopy() {
        return true;
    }

    @Override
    public SystemAction getPreferredAction() {
        return SystemAction.get(Actions.OpenSymbolChart.class);
    }
    
    @Override
    public Action[] getActions(boolean popup) {
        return new Action[] {
                SystemAction.get(Actions.OpenSymbolChart.class),
                SystemAction.get(Actions.ChartAllInFolder.class),
                //SystemAction.get(Actions.AddTo.class),
                org.openide.awt.Actions.forID("Import", "one.chartsy.actions.AsciiImportAction"),
                null,
                SystemAction.get(NodeActions.CollapseAll.class),
                null,
                SystemAction.get(CopyAction.class),
                SystemAction.get(CutAction.class),
                SystemAction.get(Actions.Delete.class)
        };
    }
    
//    @Override
//    public Transferable clipboardCut() throws IOException {
//        Transferable deflt = super.clipboardCut();
//        ExTransferable added = ExTransferable.create(deflt);
//        added.put(new ExTransferable.Single(SymbolExt.dataFlavor) {
//            @Override
//            protected SymbolExt getData() {
//                return getLookup().lookup(SymbolExt.class);
//            }
//        });
//        return added;
//    }
    
//    @Override
//    public Transferable clipboardCopy() throws IOException {
//        Transferable deflt = super.clipboardCopy();
//        ExTransferable added = ExTransferable.create(deflt);
//        added.put(new ExTransferable.Single(SymbolExt.dataFlavor) {
//            @Override
//            protected SymbolExt getData() {
//                return getLookup().lookup(SymbolExt.class);
//            }
//        });
//        return added;
//    }
}
