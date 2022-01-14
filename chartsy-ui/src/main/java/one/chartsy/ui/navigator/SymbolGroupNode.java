package one.chartsy.ui.navigator;

import one.chartsy.SymbolGroupContent;
import one.chartsy.persistence.domain.SymbolGroupAggregateData;
import one.chartsy.persistence.domain.model.SymbolGroupRepository;
import one.chartsy.ui.nodes.BaseNodeAction;
import one.chartsy.ui.nodes.EntityNode;
import one.chartsy.ui.nodes.NewNodeType;
import one.chartsy.ui.nodes.NodeActions;
import org.openide.actions.NewAction;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.view.BeanTreeView;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.util.Lookup;
import org.openide.util.actions.SystemAction;
import org.openide.util.datatransfer.NewType;
import org.openide.util.lookup.Lookups;
import org.springframework.context.ApplicationContext;

import javax.swing.*;
import java.util.List;

public class SymbolGroupNode extends AbstractNode implements EntityNode<SymbolGroupContent> {

    public static SymbolGroupNode create(SymbolGroupContent sg, ExplorerManager m, BeanTreeView v, ApplicationContext ctx) {
        SymbolGroupChildFactory childFactory = new SymbolGroupChildFactory(ctx, sg);
        return new SymbolGroupNode(sg, childFactory, Lookups.fixed(sg, childFactory, m, v, ctx));
    }

    public static SymbolGroupNode create(SymbolGroupContent sg, ChildFactory<? extends SymbolGroupContent> cf) {
        return new SymbolGroupNode(sg, cf, Lookups.fixed(sg, cf));
    }

    private static final String SYMBOL_GROUP_ICON_BASE = "one/chartsy/ui/navigator/symbol-group.png";

    private final SymbolGroupContent symbolGroup;
    private final ChildFactory<?> childFactory;

    protected SymbolGroupNode(SymbolGroupContent sg, ChildFactory<? extends SymbolGroupContent> cf, Lookup lookup) {
        this(sg, cf, lookup, true);
    }

    protected SymbolGroupNode(SymbolGroupContent symbolGroup, ChildFactory<? extends SymbolGroupContent> childFactory, Lookup lookup, boolean async) {
        super(Children.create(childFactory, async), lookup);
        this.symbolGroup = symbolGroup;
        this.childFactory = childFactory;
        setName(symbolGroup.getName());
        setIconBaseWithExtension(SYMBOL_GROUP_ICON_BASE);
        String typeName = symbolGroup.getTypeName();
        //if (symbolGroup.getStereotype() == Stereotype.FAVORITES)
        //    setIconBaseWithExtension("one/chartsy/resources/star.green.16.png");
        //else
        //if (type.getIcon() != null)
        //    setIconBaseWithExtension(type.getIcon());
    }

    @Override
    public SymbolGroupContent getEntity() {
        return symbolGroup;
    }

    @Override
    public Comparable<?> getEntityIdentifier() {
        return getEntity().getId();
    }

    @Override
    public ChildFactory<?> getChildFactory() {
        return childFactory;
    }

    @Override
    public Action[] getActions(boolean context) {
        return new Action[] {
                SystemAction.get(NewAction.class),
                SystemAction.get(Actions.AddSymbolGroup.class),
                null,
//                SystemAction.get(CopyAction.class),
//                SystemAction.get(PasteAction.class),
//                SystemAction.get(CutAction.class),
                SystemAction.get(Actions.Delete.class),
//                SystemAction.get(Actions.EraseFolder.class),
//                SystemAction.get(RenameAction.class),
//                SystemAction.get(ReorderActionExt.class),
//                null,
                SystemAction.get(NodeActions.ExpandAll.class),
                SystemAction.get(NodeActions.CollapseAll.class),
//                SystemAction.get(Actions.ConvertToRegularFolder.class),
//                SystemAction.get(Actions.CloseFolder.class)
        };
    }

    @Override
    public NewType[] getNewTypes() {
        return List.of(
                new NewNodeType(this, "NewTypes.SymbolGroup", Actions.AddSymbolGroup.class),
                new NewNodeType(this, "NewTypes.DataProviderFromDescriptor", Actions.AddDataProviderFromDescriptor.class)

        ).toArray(NewType[]::new);
    }

    @Override
    public boolean canDestroy() {
        return true;
    }

    @Override
    public void destroy() {
        BaseNodeAction.getApplicationContext(this).orElseThrow()
                .getBean(SymbolGroupRepository.class).delete((SymbolGroupAggregateData) getEntity());
    }
}