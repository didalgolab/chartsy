/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.navigator;

import one.chartsy.SymbolGroupContent;
import one.chartsy.ui.nodes.NodeActions;
import org.openide.actions.*;
import org.openide.util.Lookup;
import org.openide.util.actions.SystemAction;
import org.openide.util.lookup.Lookups;
import org.springframework.context.ApplicationContext;

import javax.swing.*;

public class SymbolGroupDataProviderNode extends SymbolGroupNode {

    private static final String SYMBOL_GROUP_DATA_PROVIDER_ICON_BASE = "one/chartsy/ui/navigator/data-provider.png";

    public SymbolGroupDataProviderNode(ApplicationContext ctx, SymbolGroupContent categoryFile) {
        this(categoryFile, new SymbolGroupChildFactory(ctx, categoryFile));
    }
    
    private SymbolGroupDataProviderNode(SymbolGroupContent categoryFile, SymbolGroupChildFactory childFactory) {
        this(categoryFile, childFactory, Lookups.fixed(categoryFile, childFactory));
    }
    
    protected SymbolGroupDataProviderNode(SymbolGroupContent symbolGroup, SymbolGroupChildFactory childFactory, Lookup lookup) {
        super(symbolGroup, childFactory, lookup);
        setName(symbolGroup.getName());
        setIconBaseWithExtension(SYMBOL_GROUP_DATA_PROVIDER_ICON_BASE);
    }
    
//    @Override
//    public Action getPreferredAction() {
//        return SystemAction.get(Actions.NewChart.class);
//    }
//

    @Override
    public Action[] getActions(boolean context) {
        return new Action[] {
                //SystemAction.get(Actions.UpgradeFile.class),
                SystemAction.get(NewAction.class),
                SystemAction.get(Actions.AddSymbolGroup.class),
                //SystemAction.get(Actions.TypeInSymbols.class),
                //SystemAction.get(Actions.NewChart.class),
                null,
                SystemAction.get(CopyAction.class),
                SystemAction.get(PasteAction.class),
                SystemAction.get(CutAction.class),
                SystemAction.get(Actions.Delete.class),
                SystemAction.get(RenameAction.class),
                //SystemAction.get(ReorderActionExt.class),
                null,
                SystemAction.get(NodeActions.ExpandAll.class),
                //SystemAction.get(Actions.CloseFolder.class)
        };
    }
}
