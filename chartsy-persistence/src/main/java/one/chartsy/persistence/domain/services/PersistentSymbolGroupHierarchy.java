/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence.domain.services;

import one.chartsy.SymbolGroupContent;
import one.chartsy.kernel.SymbolGroupHierarchy;
import one.chartsy.persistence.domain.SymbolGroupAggregateData;
import one.chartsy.persistence.domain.model.SymbolGroupRepository;
import org.openide.util.NbBundle;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class PersistentSymbolGroupHierarchy implements SymbolGroupHierarchy {

    @Autowired
    private SymbolGroupRepository repository;

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
        List<SymbolGroupAggregateData> roots = repository.findByParentGroupId(null);
        if (!roots.isEmpty())
            return roots.get(0);

        SymbolGroupAggregateData root = new SymbolGroupAggregateData();
        root.setName(NbBundle.getMessage(getClass(), "SG.root.name"));
        root.setContentType(SymbolGroupContent.Type.FOLDER);
        root = repository.save(root);
        return root;
    }
}
