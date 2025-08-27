/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation.engine.price;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.api.messages.TradeBar;
import one.chartsy.financial.price.PriceHandle;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.MarketMessageHandler;

public class PlaybackPriceHandle implements PriceHandle, MarketMessageHandler {

    protected final SymbolIdentity symbol;
    private double lastTradePrice;
    private long lastTradeTime;
    private Candle lastBar;


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

    @Override
    public void onMarketMessage(MarketEvent event) {
        if (event instanceof TradeBar barEvent) {
            var bar = barEvent.get();
            this.lastBar = bar;
            this.lastTradePrice = bar.close();
            this.lastTradeTime = bar.getTime();
        }
    }
}
