/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade;

import one.chartsy.When;
import one.chartsy.core.event.ListenerList;
import one.chartsy.financial.SymbolIdentifier;

import java.util.*;

public class MarketUniverseObserver implements MarketUniverse {
    private static final Object PRESENT = new Object();

    private final ListenerList<MarketUniverseChangeListener> listeners = ListenerList.of(MarketUniverseChangeListener.class);
    private final Map<When, Object> marketMap = new IdentityHashMap<>();
    private final Set<SymbolIdentifier> activeSymbols = new HashSet<>();
    private Set<SymbolIdentifier> activeSymbolsReadonlyView;


    public void addListener(MarketUniverseChangeListener listener) {
        listeners.addListener(listener);
    }

    public void removeListener(MarketUniverseChangeListener listener) {
        listeners.removeListener(listener);
    }

    @Override
    public Set<SymbolIdentifier> activeSymbols() {
        if (activeSymbolsReadonlyView == null)
            activeSymbolsReadonlyView = Collections.unmodifiableSet(activeSymbols);
        return activeSymbolsReadonlyView;
    }

    protected SymbolIdentifier toIdentifier(When when) {
        return new SymbolIdentifier(when.getSymbol());
    }

    protected MarketUniverseChangeEvent describeChange(When when, SymbolIdentifier added, SymbolIdentifier removed) {
        var universe = new MarketUniverse.Of(activeSymbols);
        return new MarketUniverseChangeEvent(universe, ((added != null)? when: null), ((removed != null)? when: null), added, removed);
    }

    public boolean addMarket(When when) {
        if (marketMap.putIfAbsent(when, PRESENT) == null) {
            SymbolIdentifier added = toIdentifier(when);
            if (!activeSymbols.add(added))
                added = null;

            if (!listeners.isEmpty())
                listeners.fire().onMarketUniverseChange(describeChange(when, added, null));
            return true;
        }
        return false;
    }

    public boolean removeMarket(When when) {
        if (marketMap.remove(when) == PRESENT) {
            SymbolIdentifier removed = toIdentifier(when);
            if (!activeSymbols.remove(removed))
                removed = null;

            if (!listeners.isEmpty())
                listeners.fire().onMarketUniverseChange(describeChange(when, null, removed));
            return true;
        }
        return false;
    }
}
