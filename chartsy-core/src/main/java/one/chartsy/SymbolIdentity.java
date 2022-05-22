/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import one.chartsy.naming.SymbolIdentityComparator;
import one.chartsy.naming.SymbolIdentityGenerator;
import org.openide.util.Lookup;

import java.util.Comparator;
import java.util.Optional;

/**
 * Represents a Symbol identified by {@code name} and {@code type}.
 */
public interface SymbolIdentity {

    String name();

    Optional<AssetClass> type();


    static SymbolIdentity of(String name) {
        return of(name, IdentifierType.Standard.TICKER);
    }

    static SymbolIdentity of(String name, AssetClass type) {
        return of(name, type, IdentifierType.Standard.TICKER);
    }

    static SymbolIdentity of(String name, AssetClass type, IdentifierType identifierType) {
        return generator().generate(name, type, identifierType);
    }

    static SymbolIdentity of(String name, IdentifierType identifierType) {
        return of(name, null, identifierType);
    }

    static Comparator<SymbolIdentity> comparator() {
        return Lookup.getDefault().lookup(SymbolIdentityComparator.class);
    }

    private static SymbolIdentityGenerator generator() {
        return Lookup.getDefault().lookup(SymbolIdentityGenerator.class);
    }
}
