/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.naming;

import one.chartsy.IdentifierType;
import one.chartsy.AssetClass;
import one.chartsy.SymbolIdentity;

import java.util.Optional;

public record SymbolIdentifier(
        String name,
        Optional<AssetClass> type,
        IdentifierType identifierType)
        implements SymbolIdentity, Comparable<SymbolIdentifier>
{

    public SymbolIdentifier(SymbolIdentity symbol) {
        this(symbol, IdentifierType.Standard.TICKER);
    }

    public SymbolIdentifier(SymbolIdentity symbol, IdentifierType identifierType) {
        this(symbol.name(), symbol.type(), identifierType);
    }

    public SymbolIdentifier(String name) {
        this(name, Optional.empty(), IdentifierType.Standard.TICKER);
    }

    public SymbolIdentifier(String name, Optional<AssetClass> type) {
        this(name, type, IdentifierType.Standard.TICKER);
    }

    @Override
    public int compareTo(SymbolIdentifier o) {
        return SymbolIdentityComparator.LazyHolder.INSTANCE.compare(this, o);
    }

    @Override
    public String toString() {
        if (type().isEmpty())
            return name();
        else
            return name() + "." + type().get();
    }
}
