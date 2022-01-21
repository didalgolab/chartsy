package one.chartsy.persistence.domain.services;

import one.chartsy.SymbolGroupContent;
import one.chartsy.kernel.SymbolGroupHierarchy;
import one.chartsy.persistence.domain.SymbolGroupAggregateData;
import org.openide.util.NbBundle;

public class PersistentSymbolGroupHierarchy implements SymbolGroupHierarchy {

    /**
     * The default root context (top-most symbol group node) that is used by the
     * {@code SymbolsTab} and is shared by other components.
     */
    private volatile SymbolGroupAggregateData rootContext;

    @Override
    public SymbolGroupAggregateData getRootContext() {
        if (rootContext == null) {
            synchronized (this) {
                if (rootContext == null)
                    rootContext = createRootContext();
            }
        }
        return rootContext;
    }

    protected SymbolGroupAggregateData createRootContext() {
        SymbolGroupAggregateData root = new SymbolGroupAggregateData();
        root.setName(NbBundle.getMessage(getClass(), "SG.root.name"));
        root.setContentType(SymbolGroupContent.Type.FOLDER);
        return root;
    }
}
