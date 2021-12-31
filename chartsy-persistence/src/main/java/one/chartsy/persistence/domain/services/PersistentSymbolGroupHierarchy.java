package one.chartsy.persistence.domain.services;

import one.chartsy.kernel.SymbolGroupHierarchy;
import one.chartsy.persistence.domain.SymbolGroupData;

public class PersistentSymbolGroupHierarchy implements SymbolGroupHierarchy {

    /**
     * The default root context (top-most symbol group node) that is used by the
     * {@code SymbolsTab} and is shared by other components.
     */
    private volatile SymbolGroupData rootContext;

    @Override
    public SymbolGroupData getRootContext() {
        SymbolGroupData root = new SymbolGroupData();
        root.setName("Symbols");
        return root;
    }
}
