/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation.engine.price;

import one.chartsy.SymbolIdentity;
import one.chartsy.financial.price.MarketPriceService;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.MarketMessageHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The {@code MarketPriceService} for playback/simulation mode.
 * Lazily creates and caches {@code PlaybackPriceHandle} per symbol.
 *
 * @author Mariusz Bernacki
 */
public class PlaybackMarketPriceService implements MarketPriceService, MarketMessageHandler {

    protected final Map<SymbolIdentity, PlaybackPriceHandle> prices = new HashMap<>();

    @Override
    public PlaybackPriceHandle getInstrumentPrices(SymbolIdentity symbol) {
        return prices.computeIfAbsent(symbol, PlaybackPriceHandle::new);
    }

    @Override
    public boolean hasInstrumentPrices(SymbolIdentity symbol) {
        return prices.containsKey(symbol);
    }

    public Optional<PlaybackPriceHandle> getInstrumentPricesIfExist(SymbolIdentity symbol) {
        return Optional.ofNullable(prices.get(symbol));
    }

    @Override
    public void onMarketMessage(MarketEvent event) {
        getInstrumentPrices(event.symbol()).onMarketMessage(event);
    }
}
