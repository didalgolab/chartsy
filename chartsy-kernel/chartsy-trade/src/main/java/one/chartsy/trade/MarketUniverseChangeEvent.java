/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade;

import lombok.Getter;
import one.chartsy.When;
import one.chartsy.financial.SymbolIdentifier;

import java.util.List;

@Getter
public class MarketUniverseChangeEvent {

    private final MarketUniverse universe;
    private final List<When> addedMarkets;
    private final List<When> removedMarkets;
    private final List<SymbolIdentifier> addedSymbols;
    private final List<SymbolIdentifier> removedSymbols;


    public MarketUniverseChangeEvent(MarketUniverse universe,
                                     List<When> addedMarkets,
                                     List<When> removedMarkets,
                                     List<SymbolIdentifier> addedSymbols,
                                     List<SymbolIdentifier> removedSymbols)
    {
        this.universe = universe;
        this.addedMarkets = List.copyOf(addedMarkets);
        this.removedMarkets = List.copyOf(removedMarkets);
        this.addedSymbols = List.copyOf(addedSymbols);
        this.removedSymbols = List.copyOf(removedSymbols);
    }

    public MarketUniverseChangeEvent(MarketUniverse universe,
                                     When addedMarket,
                                     When removedMarket,
                                     SymbolIdentifier addedSymbol,
                                     SymbolIdentifier removedSymbol)
    {
        this.universe = universe;
        this.addedMarkets   = (addedMarket   == null)? List.of() : List.of(addedMarket);
        this.removedMarkets = (removedMarket == null)? List.of() : List.of(removedMarket);
        this.addedSymbols   = (addedSymbol   == null)? List.of() : List.of(addedSymbol);
        this.removedSymbols = (removedSymbol == null)? List.of() : List.of(removedSymbol);
    }

    public boolean hasAddedMarkets() {
        return !getAddedMarkets().isEmpty();
    }

    public boolean hasRemovedMarkets() {
        return !getRemovedMarkets().isEmpty();
    }

    public boolean hasAddedSymbols() {
        return !getAddedSymbols().isEmpty();
    }

    public boolean hasRemovedSymbols() {
        return !getRemovedSymbols().isEmpty();
    }
}