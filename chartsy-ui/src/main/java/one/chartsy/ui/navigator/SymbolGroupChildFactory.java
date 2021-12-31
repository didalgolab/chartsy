/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.navigator;

import java.util.List;
import java.util.Optional;

import one.chartsy.*;
import one.chartsy.core.Refreshable;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;

public class SymbolGroupChildFactory extends ChildFactory<SymbolGroupContent> implements Refreshable {
    protected final SymbolGroupContent group;

    public SymbolGroupChildFactory(SymbolGroupContent group) {
        this.group = group;
    }

    @Override
    protected boolean createKeys(List<SymbolGroupContent> toPopulate) {
        try {
            //List<SymbolGroupContent> content = group.getContent(link);
            //toPopulate.addAll(content);

        } catch (Exception e) {
            Exceptions.printStackTrace(e);
        }
        return true;
    }
    
    @Override
    protected Node createNodeForKey(SymbolGroupContent group) {
        Optional<SymbolIdentity> symbol = group.getSymbol();
//        if (symbol.isPresent() && symbol.get() instanceof Symbol)
//            return new SymbolNode((Symbol)symbol.get());
        
//        SymbolGroupData group = (SymbolGroupData) group;
//        if (group.getId() == 0)
//            return new SymbolGroupDataProviderFolderNode(group);

        switch (group.getTypeName()) {
//        case LOCAL_DATA_PROVIDER:
//            return new SymbolGroupLocalDataProviderNode(group);
//        case DATA_PROVIDER:
//            return new SymbolGroupDataProviderNode(group);
//        case DATA_PROVIDER_FOLDER:
//            return new SymbolGroupDataProviderFolderNode(group);
//        case CLOSED_FOLDER:
//            return new SymbolGroupClosedFolderNode(group);
        case "FOLDER":
        default:
            return SymbolGroupNode.from(group, new SymbolGroupChildFactory(group));
        }
    }

    @Override
    public void refresh() {
        super.refresh(false);
    }
}
