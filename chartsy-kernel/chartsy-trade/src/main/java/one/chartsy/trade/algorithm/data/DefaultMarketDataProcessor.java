/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm.data;

import one.chartsy.SymbolIdentity;
import one.chartsy.messaging.MarketEvent;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Default implementation of {@code MarketDataProcessor}.
 *
 * @param <T> the type of instrument data
 */
public class DefaultMarketDataProcessor<T extends InstrumentData> implements MarketDataProcessor<T> {

    private final InstrumentDataFactory<T> dataFactory;
    private final Map<SymbolIdentity, T> instruments = new HashMap<>();
    private final Collection<T> instrumentsView = Collections.unmodifiableCollection(instruments.values());

    /**
     * Constructs a processor with the specified factory and subscribed symbols.
     *
     * @param dataFactory           factory to create instrument data
     */
    public DefaultMarketDataProcessor(InstrumentDataFactory<T> dataFactory) {
        this.dataFactory = dataFactory;
    }

    @Override
    public int getInstrumentCount() {
        return instruments.size();
    }

    @Override
    public final Collection<T> getInstruments() {
        return instrumentsView;
    }

    @Override
    public T getOrCreate(SymbolIdentity symbol) {
        var instrument = instruments.get(symbol);
        if (instrument == null) {
            instruments.put(symbol, (instrument = dataFactory.create(symbol)));
        }

        return instrument;
    }

    @Override
    public void onMarketMessage(MarketEvent msg) {
        getOrCreate(msg.symbol()).onMarketMessage(msg);
    }

    @Override
    public Iterator<T> iterator() {
        return instruments.values().iterator();
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        instruments.values().forEach(action);
    }
}
