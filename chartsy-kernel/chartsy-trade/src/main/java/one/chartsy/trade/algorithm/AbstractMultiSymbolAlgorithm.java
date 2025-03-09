/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm;

import one.chartsy.SymbolData;
import one.chartsy.SymbolDataRanker;
import one.chartsy.SymbolIdentity;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.time.Chronological;
import one.chartsy.time.DateChangeSignal;

public abstract class AbstractMultiSymbolAlgorithm<T> extends AbstractAlgorithm {

    private final DateChangeSignal dateChange = DateChangeSignal.create();
    private final SymbolDataRanker<T> ranker = new SymbolDataRanker<>();

    protected AbstractMultiSymbolAlgorithm(AlgorithmContext context) {
        super(context);
    }

    public abstract T createCustomData(SymbolIdentity symbol);

    public final SymbolDataRanker<T> getRanker() {
        return ranker;
    }

    public SymbolData<T> getSymbolData(SymbolIdentity symbol) {
        return getRanker().getSymbolData(symbol, this::createCustomData);
    }

    public T getCustomData(SymbolIdentity symbol) {
        return getSymbolData(symbol).customData();
    }

    @Override
    public void onMarketMessage(MarketEvent message) {
        if (dateChange.poll(message)) {
            onDateChange(message);
        }
    }

    protected void onDateChange(Chronological current) {
        doRankInstruments();
    }

    protected void doRankInstruments() {
        getRanker().rankInstruments();
    }
}
