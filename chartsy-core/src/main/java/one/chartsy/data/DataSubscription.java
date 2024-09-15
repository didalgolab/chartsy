/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a subscription to a set of data streams, symbols, and message types.
 * This interface is typically used in a trading framework to specify what data
 * an algorithm or component should subscribe to.
 *
 * @author Mariusz Bernacki
 */
public interface DataSubscription {

    /**
     * A DataSubscription instance subscribed to all symbols.
     */
    DataSubscription SUBSCRIBED_TO_ALL = DataSubscription.of(null);

    /**
     * Gives the list of data streams to subscribe to. Each stream typically represents
     * a specific data feed, such as a market feed from an exchange.
     *
     * @return the names of data stream the system is subscribed to
     */
    Set<String> streams();

    /**
     * Gives the list of symbols to subscribe to. Each symbol typically represents
     * a specific financial instrument, such as a futures contract or stock.
     * If null, it indicates a subscription to all symbols.
     *
     * @return the symbols the system is subscribed to
     */
    Set<String> symbols();

    /**
     * Gives the list of message types to subscribe to. Each type typically represents
     * a specific kind of data message, such as trades, quotes, or order book updates.
     *
     * @return the message classes
     */
    Set<Class<?>> types();

    /**
     * Checks if this subscription is for all symbols.
     *
     * @return {@code true} if subscribed to all symbols, {@code false} otherwise
     */
    default boolean isSubscribedToAllSymbols() {
        return symbols() == null;
    }

    /**
     * Checks if this subscription includes the specified symbol.
     *
     * @param symbol the symbol to check
     * @return {@code true} if the subscription includes the symbol or is subscribed to all symbols,
     *         {@code false} otherwise
     * @throws NullPointerException if the specified symbol is null
     */
    default boolean isSubscribedTo(String symbol) {
        Objects.requireNonNull(symbol, "symbol");
        Set<String> syms;
        return isSubscribedToAllSymbols() || (syms = symbols()) != null && syms.contains(symbol);
    }

    /**
     * Creates a new immutable {@code DataSubscription} instance.
     *
     * @param symbols the list of symbols (null indicates subscription to all symbols)
     * @return an immutable {@code DataSubscription} instance
     */
    static DataSubscription of(Collection<String> symbols) {
        return of(symbols, List.of(), List.of());
    }

    /**
     * Creates a new immutable {@code DataSubscription} instance.
     *
     * @param symbols the list of symbols (null indicates subscription to all symbols)
     * @param streams the list of data streams (must not be null)
     * @param types   the list of message types (must not be null)
     * @return an immutable {@code DataSubscription} instance
     */
    static DataSubscription of(Collection<String> symbols, Collection<String> streams, Collection<Class<?>> types) {
        Objects.requireNonNull(streams, "streams");
        Objects.requireNonNull(types, "types");

        return new Of((symbols == null) ? null : Set.copyOf(symbols), Set.copyOf(streams), Set.copyOf(types));
    }

    /**
     * An immutable record implementation of {@link DataSubscription}.
     */
    record Of(Set<String> symbols, Set<String> streams, Set<Class<?>> types) implements DataSubscription {
    }
}
