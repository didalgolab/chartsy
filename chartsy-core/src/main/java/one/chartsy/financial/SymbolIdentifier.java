/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial;

import one.chartsy.SymbolIdentity;

/**
 * A concrete implementation of {@link SymbolIdentity} that represents a symbol with a name and type.
 * <p>
 * This record provides a convenient and immutable representation of a symbol, suitable for use
 * in various financial applications. It implements the {@link Comparable} interface, allowing
 * sorting and comparison of symbol identifiers.
 *
 * @author Mariusz Bernacki
 *
 */
public record SymbolIdentifier(
        String name,
        IdentityType type)
        implements SymbolIdentity, Comparable<SymbolIdentifier>
{

    public SymbolIdentifier(SymbolIdentity symbol) {
        this(symbol.name(), symbol.type());
    }

    public SymbolIdentifier(String name) {
        this(name, InstrumentType.CUSTOM);
    }

    @Override
    public int compareTo(SymbolIdentifier o) {
        return SymbolIdentityComparator.LazyHolder.INSTANCE.compare(this, o);
    }

    @Override
    public String toString() {
        return name() + "." + type();
    }
}
