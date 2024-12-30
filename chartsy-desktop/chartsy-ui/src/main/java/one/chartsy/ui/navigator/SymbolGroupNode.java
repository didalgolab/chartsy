/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
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
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.actions.SystemAction;
import org.openide.util.datatransfer.NewType;
import org.openide.util.datatransfer.PasteType;
import org.openide.util.lookup.Lookups;
import org.springframework.context.ApplicationContext;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

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
        SymbolGroupContent.Type contentType = symbolGroup.getContentType();
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

    @SuppressWarnings("unchecked")
    @Override
    protected void createPasteTypes(Transferable t, List<PasteType> s) {
        try {
            if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
                s.add(PasteTypes.createFileListPasteType(this, (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor)));

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (UnsupportedFlavorException e) {
            throw new InternalError("Shouldn't happen", e);
        }
        super.createPasteTypes(t, s);
    }

    public static class PasteTypes {
        private static final Pattern STOOQ_FILE_NAME_PATTERN = Pattern
                .compile("[dh5]_(hk|hu|jp|macro|pl|uk|us|world)_txt(\\s?\\([0-9]+\\))?\\.zip");

        public static PasteType createFileListPasteType(Node node, List<File> files) {
            List<Path> paths = new LinkedList<>();
            for (File file : files)
                if (STOOQ_FILE_NAME_PATTERN.matcher(file.getName()).matches())
                    paths.add(file.toPath());

            return new PasteType() {
                @Override
                public Transferable paste() {
                    for (Path path : paths) {
                        String expr = "T(one.chartsy.data.provider.file.FlatFileFormat).STOOQ.newDataProvider('"+path.toAbsolutePath().toString().replace('\\','/')+"')";
                        Actions.createDataProviderFromDescriptor(node, expr);
                    }
                    return null;
                }
            };
        }
    }
}
