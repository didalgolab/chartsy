package one.chartsy.ui.navigator;

import java.util.List;
import java.util.Optional;

import one.chartsy.*;
import one.chartsy.core.Refreshable;
import one.chartsy.data.provider.DataProviderLoader;
import one.chartsy.persistence.domain.model.SymbolGroupRepository;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.springframework.context.ApplicationContext;

public class SymbolGroupChildFactory extends ChildFactory<SymbolGroupContent> implements Refreshable {
    protected final ApplicationContext context;
    protected final SymbolGroupContent group;

    public SymbolGroupChildFactory(ApplicationContext context, SymbolGroupContent group) {
        this.context = context;
        this.group = group;
    }

    @Override
    protected boolean createKeys(List<SymbolGroupContent> toPopulate) {
        try {
            SymbolGroupRepository repository = context.getBean(SymbolGroupRepository.class);
            DataProviderLoader loader = context.getBean(DataProviderLoader.class);
            List<SymbolGroupContent> content = group.getContent(repository, loader);
            toPopulate.addAll(content);

        } catch (Exception e) {
            Exceptions.printStackTrace(e);
        }
        return true;
    }
    
    @Override
    protected Node createNodeForKey(SymbolGroupContent group) {
        Optional<Symbol> symbol = group.getAsSymbol();
//        if (symbol.isPresent() && symbol.get() instanceof Symbol)
//            return new SymbolNode((Symbol)symbol.get());
        
//        SymbolGroupData group = (SymbolGroupData) group;
//        if (group.getId() == 0)
//            return new SymbolGroupDataProviderFolderNode(group);

        switch (group.getContentType()) {
            case SYMBOL:
                return new SymbolNode((Symbol) symbol.get());
            case DATA_PROVIDER:
                return new SymbolGroupDataProviderNode(context, group);
            case DATA_PROVIDER_FOLDER:
                return new SymbolGroupDataProviderFolderNode(context, group);
    //        case CLOSED_FOLDER:
    //            return new SymbolGroupClosedFolderNode(group);
            case FOLDER:
            //default:
                return SymbolGroupNode.create(group, new SymbolGroupChildFactory(context, group));
        }
        return null;
    }

    @Override
    public void refresh() {
        super.refresh(false);
    }
}
