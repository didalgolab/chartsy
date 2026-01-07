/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.data.TradeBar;
import one.chartsy.trade.algorithm.data.AbstractInstrumentData;
import one.chartsy.trade.algorithm.data.InstrumentDataFactory;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Generic scan algorithm that captures per-symbol state and produces a result on close.
 *
 * @param <S> per-symbol state type
 * @param <R> result type
 */
public final class MarketScanAlgorithm<S, R> extends AbstractAlgorithm<MarketScanAlgorithm.ScanInstrumentData<S>> {
    private final MarketScan.StateFactory<S> stateFactory;
    private final MarketScan.OnBar<S> onBar;
    private final MarketScan.OnMarketEvent<S> onMarketEvent;
    private final MarketScan.Finisher<S, R> finisher;
    private final AtomicReference<R> resultRef;

    public MarketScanAlgorithm(AlgorithmContext context,
                               MarketScan.StateFactory<S> stateFactory,
                               MarketScan.OnBar<S> onBar,
                               MarketScan.OnMarketEvent<S> onMarketEvent,
                               MarketScan.Finisher<S, R> finisher,
                               AtomicReference<R> resultRef) {
        super(context);
        this.stateFactory = Objects.requireNonNull(stateFactory, "stateFactory is null");
        this.onBar = onBar;
        this.onMarketEvent = onMarketEvent;
        this.finisher = Objects.requireNonNull(finisher, "finisher is null");
        this.resultRef = Objects.requireNonNull(resultRef, "resultRef is null");
    }

    @Override
    protected InstrumentDataFactory<ScanInstrumentData<S>> createInstrumentDataFactory() {
        return symbol -> new ScanInstrumentData<>(symbol, stateFactory.create(symbol), onBar, onMarketEvent);
    }

    @Override
    public void close() {
        super.close();
        Map<SymbolIdentity, MarketScan.ScanSnapshot<S>> states = new TreeMap<>(SymbolIdentity.comparator());
        for (ScanInstrumentData<S> data : marketDataProcessor.getInstruments()) {
            states.put(data.symbol(), data);
        }
        resultRef.set(finisher.finish(states));
    }

    public static final class ScanInstrumentData<S> extends AbstractInstrumentData implements MarketScan.ScanSnapshot<S> {
        final S state;
        final MarketScan.OnBar<S> onBar;
        final MarketScan.OnMarketEvent<S> onMarketEvent;
        Candle lastBar;

        ScanInstrumentData(SymbolIdentity symbol,
                           S state,
                           MarketScan.OnBar<S> onBar,
                           MarketScan.OnMarketEvent<S> onMarketEvent) {
            super(symbol);
            this.state = state;
            this.onBar = onBar;
            this.onMarketEvent = onMarketEvent;
        }

        @Override
        public void onMarketMessage(MarketEvent event) {
            if (event instanceof TradeBar tradeBar) {
                Candle candle = tradeBar.get();
                lastBar = candle;
                if (onBar != null)
                    onBar.accept(symbol, state, candle);
            }
            if (onMarketEvent != null)
                onMarketEvent.accept(symbol, state, event);
        }

        @Override
        public SymbolIdentity symbol() {
            return symbol;
        }

        @Override
        public S state() {
            return state;
        }

        @Override
        public Candle lastBar() {
            return lastBar;
        }
    }
}
