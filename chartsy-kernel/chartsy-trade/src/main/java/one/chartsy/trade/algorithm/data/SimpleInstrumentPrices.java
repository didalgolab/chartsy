/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm.data;

import one.chartsy.SymbolIdentity;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.data.BestBidOfferEvent;
import one.chartsy.messaging.data.TradeBar;
import one.chartsy.messaging.data.TradeTick;

public class SimpleInstrumentPrices extends AbstractInstrumentData {
    protected double lastTradePrice = Double.NaN;
    protected double lastTradeTime;

    public SimpleInstrumentPrices(SymbolIdentity symbol) {
        super(symbol);
    }

    public boolean hasPrice() {
        return !Double.isNaN(lastTradePrice);
    }

    public final double getLastTradePrice() {
        return lastTradePrice;
    }

    public final double getLastTradeTime() {
        return lastTradeTime;
    }

    @Override
    public void onMarketMessage(MarketEvent event) {
        if (event instanceof TradeBar bar) {
            onTradeBar(bar);
        } else if (event instanceof TradeTick tick) {
            onTradeTick(tick);
        } else if (event instanceof BestBidOfferEvent bestBidOffer) {
            onBestBidOffer(bestBidOffer);
        } else {
            onCustomEvent(event);
        }
    }

    public void onTradeBar(TradeBar bar) {
        this.lastTradePrice = bar.get().close();
        this.lastTradeTime = bar.getTime();
    }

    public void onTradeTick(TradeTick tick) {
        this.lastTradePrice = tick.get().price();
        this.lastTradeTime = tick.getTime();
    }

    public void onBestBidOffer(BestBidOfferEvent bestBidOffer) {
    }

    public void onCustomEvent(MarketEvent event) {
    }
}
