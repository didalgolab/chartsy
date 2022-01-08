package one.chartsy.persistence.domain.services;

import one.chartsy.kernel.SymbolGroupHierarchy;
import one.chartsy.persistence.domain.SymbolGroupAggregateData;

public class PersistentSymbolGroupHierarchy implements SymbolGroupHierarchy {

    /**
     * The default root context (top-most symbol group node) that is used by the
     * {@code SymbolsTab} and is shared by other components.
     */
    private volatile SymbolGroupAggregateData rootContext;

    @Override
    public SymbolGroupAggregateData getRootContext() {
        SymbolGroupAggregateData root = new SymbolGroupAggregateData();
        root.setName("Symbols");
        root.setTypeName("FOLDER");
        return root;
    }
}
