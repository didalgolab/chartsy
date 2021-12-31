package one.chartsy.ui.navigator;

import one.chartsy.SymbolGroupContent;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.view.BeanTreeView;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

public class SymbolGroupNode extends AbstractNode {

    public static SymbolGroupNode from(SymbolGroupContent sg, ExplorerManager m, BeanTreeView v) {
        SymbolGroupChildFactory childFactory = new SymbolGroupChildFactory(sg);
        return new SymbolGroupNode(sg, childFactory, Lookups.fixed(sg, childFactory, m, v));
    }

    public static SymbolGroupNode from(SymbolGroupContent sg, ChildFactory<? extends SymbolGroupContent> cf) {
        return new SymbolGroupNode(sg, cf, Lookups.fixed(sg, cf));
    }

    private final SymbolGroupContent symbolGroup;

    protected SymbolGroupNode(SymbolGroupContent sg, ChildFactory<? extends SymbolGroupContent> cf, Lookup lookup) {
        this(sg, cf, lookup, true);
    }

    protected SymbolGroupNode(SymbolGroupContent symbolGroup, ChildFactory<? extends SymbolGroupContent> cf, Lookup lookup, boolean async) {
        super(Children.create(cf, async), lookup);
        this.symbolGroup = symbolGroup;
        setName(symbolGroup.getName());
        String typeName = symbolGroup.getTypeName();
        //if (symbolGroup.getStereotype() == Stereotype.FAVORITES)
        //    setIconBaseWithExtension("one/chartsy/resources/star.green.16.png");
        //else
        //if (type.getIcon() != null)
        //    setIconBaseWithExtension(type.getIcon());
    }
}
