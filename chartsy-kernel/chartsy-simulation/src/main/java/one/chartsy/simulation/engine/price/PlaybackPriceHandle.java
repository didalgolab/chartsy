/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation.engine.price;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.financial.price.PriceHandle;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.MarketMessageHandler;
import one.chartsy.messaging.data.TradeBar;
import one.chartsy.messaging.data.BestBidOfferEvent;
import one.chartsy.messaging.data.BarOpen;

public class PlaybackPriceHandle implements PriceHandle, MarketMessageHandler {

    protected final SymbolIdentity symbol;
    private double lastTradePrice;
    private long lastTradeTime;
    private Candle lastBar;
    private QuoteSnapshot quote = QuoteSnapshot.EMPTY;


    public PlaybackPriceHandle(SymbolIdentity symbol) {
        this.symbol = symbol;
    }

    @Override
    public final SymbolIdentity getSymbol() {
        return symbol;
    }

    @Override
    public final double getLastTradePrice() {
        return lastTradePrice;
    }

    @Override
    public long getLastTradeTime() {
        return lastTradeTime;
    }

    /** A quote never becomes a completed candle or changes the last-trade fields. */
    public QuoteSnapshot getQuote() { return quote; }

    @Override
    public void onMarketMessage(MarketEvent event) {
        if (event instanceof BestBidOfferEvent bbo) {
            quote = quote.update(bbo.get());
        } else if (event instanceof BarOpen open) {
            this.lastTradePrice = open.open();
            this.lastTradeTime = open.time();
        } else if (event instanceof TradeBar barEvent) {
            var bar = barEvent.get();
            this.lastBar = bar;
            this.lastTradePrice = bar.close();
            this.lastTradeTime = bar.time();
        }
    }
}
