/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.messaging.data;

import one.chartsy.SymbolIdentity;
import one.chartsy.data.market.BestBidOfferQuote;
import one.chartsy.messaging.MarketDataEvent;

public interface BestBidOfferEvent extends MarketDataEvent<BestBidOfferQuote> {

    record Of(SymbolIdentity symbol, BestBidOfferQuote get) implements BestBidOfferEvent { }
}
