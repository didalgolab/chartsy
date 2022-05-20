/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.navigator;

import java.util.Objects;

import javax.swing.Action;

import one.chartsy.SymbolGroupContent;
import one.chartsy.ui.nodes.EntityNode;
import one.chartsy.ui.nodes.NodeActions;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.util.actions.SystemAction;
import org.openide.util.lookup.Lookups;
import org.springframework.context.ApplicationContext;

public class SymbolGroupDataProviderFolderNode extends AbstractNode implements EntityNode<SymbolGroupContent> {
    private static final String SYMBOL_GROUP_DATA_PROVIDER_FOLDER_ICON_BASE = "one/chartsy/ui/navigator/data-provider-folder.png";
    /** The symbol group data associated with this node. */
    private final SymbolGroupContent data;

    private final SymbolGroupChildFactory childFactory;
    
    
    public SymbolGroupDataProviderFolderNode(ApplicationContext appContext, SymbolGroupContent symbolGroup) {
        this(symbolGroup, new SymbolGroupChildFactory(appContext, symbolGroup));
    }
    
    private SymbolGroupDataProviderFolderNode(SymbolGroupContent symbolGroup, SymbolGroupChildFactory childFactory) {
        super(Children.create(childFactory, true), Lookups.fixed(symbolGroup, childFactory));
        setName(symbolGroup.getName());
        setIconBaseWithExtension(SYMBOL_GROUP_DATA_PROVIDER_FOLDER_ICON_BASE);
        this.data = Objects.requireNonNull(symbolGroup, "symbolGroup");
        this.childFactory = childFactory;
    }
    
    @Override
    public final SymbolGroupContent getEntity() {
        return data;
    }
    
    @Override
    public Long getEntityIdentifier() {
        return getEntity().getId();
    }

    @Override
    public SymbolGroupChildFactory getChildFactory() {
        return childFactory;
    }

    //    public DataProvider getDataProvider() {
//        Node node = this;
//        do {
//            SymbolGroupData data = getLookup().lookup(SymbolGroupData.class);
//            if (data.getDataProviderEx() != null)
//                return data.getDataProviderEx();
//        } while ((node = node.getParentNode()) != null);
//
//        return null;
//    }
//
//    @Override
//    public Action getPreferredAction() {
//        return SystemAction.get(Actions.NewChart.class);
//    }
    
    @Override
    public Action[] getActions(boolean context) {
        return new Action[] {
//                SystemAction.get(Actions.NewChart.class),
                SystemAction.get(Actions.ChartAllInFolder.class),
//                SystemAction.get(Actions.AddSymbol.class),
                SystemAction.get(NodeActions.ExpandAll.class),
        };
    }
    
    @Override
    public boolean canCopy() {
        return true;
    }
}
