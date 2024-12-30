/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import one.chartsy.financial.IdentityType;
import one.chartsy.financial.InstrumentType;
import one.chartsy.financial.SymbolIdentityComparator;
import one.chartsy.financial.SymbolIdentityGenerator;
import org.openide.util.Lookup;

import java.util.Comparator;

/**
 * Represents a Symbol uniquely identified by its {@code name} and {@code type}.
 * <p>
 * This interface provides a standardized way to represent financial instruments or market entities
 * within the system, ensuring consistent and unambiguous referencing.
 * <p>
 * The {@code name} attribute typically represents the ticker symbol (e.g., "AAPL" for Apple Inc.)
 * or a similar identifier used in trading platforms and market data feeds. The {@code type}
 * attribute specifies the category or classification of the symbol, such as {@link InstrumentType#EQUITY}
 * for stocks or {@link InstrumentType#FUTURE} for futures contracts.
 * <p>
 * For example, to represent the Apple Inc. stock, you would create a `SymbolIdentity` with the
 * name "AAPL" and the type {@link InstrumentType#EQUITY}.
 *
 * @author Mariusz Bernacki
 * @see IdentityType
 */
public interface SymbolIdentity {

    /**
     * Returns the name of the symbol, typically the ticker symbol.
     *
     * @return the symbol name
     */
    String name();

    /**
     * The type of the financial instrument or market entity associated with the symbol.
     *
     * @return the identity type
     */
    IdentityType type();

    /**
     * Returns the name of the identity type. This is a convenience method equivalent to calling
     * {@code type().name()}.
     *
     * @return the identity type name
     */
    default String typeName() {
        return type().name();
    }

    /**
     * Creates a {@code SymbolIdentity} with the given name and a default type of #{@link InstrumentType#CUSTOM}.
     *
     * @param name the name of the symbol
     * @return a new symbol identity instance
     */
    static SymbolIdentity of(String name) {
        return of(name, InstrumentType.CUSTOM);
    }

    /**
     * Creates a {@code SymbolIdentity} with the given name and type.
     *
     * @param name the name of the symbol
     * @param type the identity type of the symbol
     * @return a new symbol identity instance
     */
    static SymbolIdentity of(String name, IdentityType type) {
        return generator().generate(name, type);
    }

    /**
     * Provides a comparator for comparing {@code SymbolIdentity} instances.
     *
     * @return a comparator for symbol identity instances
     */
    static Comparator<SymbolIdentity> comparator() {
        return Lookup.getDefault().lookup(SymbolIdentityComparator.class);
    }

    /**
     * Returns a {@link SymbolIdentityGenerator} for creating new symbol identity instances.
     *
     * @return a {@code SymbolIdentityGenerator} instance
     */
    private static SymbolIdentityGenerator generator() {
        return Lookup.getDefault().lookup(SymbolIdentityGenerator.class);
    }
}
