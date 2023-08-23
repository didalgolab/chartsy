/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.naming;

import one.chartsy.AssetClass;
import one.chartsy.SymbolIdentity;
import org.openide.util.lookup.ServiceProvider;

import java.util.Comparator;

@ServiceProvider(service = SymbolIdentityComparator.class)
public class SymbolIdentityComparator implements Comparator<SymbolIdentity> {

    @Override
    public int compare(SymbolIdentity o1, SymbolIdentity o2) {
        int cmp = o1.name().compareTo(o2.name());
        if (cmp == 0) {
            String typeName1 = o1.type().map(AssetClass::name).orElse("");
            String typeName2 = o2.type().map(AssetClass::name).orElse("");
            return typeName1.compareTo(typeName2);
        }
        return cmp;
    }

    static class LazyHolder {
        static final SymbolIdentityComparator INSTANCE = new SymbolIdentityComparator();
    }
}
