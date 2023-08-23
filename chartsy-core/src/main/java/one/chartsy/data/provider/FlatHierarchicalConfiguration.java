/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider;

import one.chartsy.SymbolGroup;

import java.util.Collections;
import java.util.List;

class FlatHierarchicalConfiguration implements HierarchicalConfiguration {
    private final DataProvider provider;

    FlatHierarchicalConfiguration(DataProvider provider) {
        this.provider = provider;
    }

    @Override
    public List<SymbolGroup> getRootGroups() {
        return List.copyOf(provider.listSymbolGroups());
    }

    @Override
    public List<SymbolGroup> getSubGroups(SymbolGroup parent) {
        return Collections.emptyList();
    }

    @Override
    public String getSimpleName(SymbolGroup group) {
        return group.name();
    }
}
