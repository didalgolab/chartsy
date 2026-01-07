/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.messaging.MarketEvent;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A lightweight wrapper for defining per-symbol scans without writing custom algorithm classes.
 *
 * @param <S> per-symbol state type
 * @param <R> result type produced at the end of the scan
 */
public final class MarketScan<S, R> {

    public interface StateFactory<S> {
        S create(SymbolIdentity symbol);
    }

    public interface ScanSnapshot<S> {

        S state();

        Candle lastBar();

        SymbolIdentity symbol();
    }

    public interface OnBar<S> {
        void accept(SymbolIdentity symbol, S state, Candle candle);
    }

    public interface OnMarketEvent<S> {
        void accept(SymbolIdentity symbol, S state, MarketEvent event);
    }

    public interface Finisher<S, R> {
        R finish(Map<SymbolIdentity, ScanSnapshot<S>> states);
    }

    public static <S, R> Builder<S, R> builder(StateFactory<S> stateFactory, Finisher<S, R> finisher) {
        return new Builder<>(stateFactory, finisher);
    }

    private final StateFactory<S> stateFactory;
    private final OnBar<S> onBar;
    private final OnMarketEvent<S> onMarketEvent;
    private final Finisher<S, R> finisher;
    private final AtomicReference<R> resultRef = new AtomicReference<>();

    private MarketScan(Builder<S, R> builder) {
        this.stateFactory = builder.stateFactory;
        this.onBar = builder.onBar;
        this.onMarketEvent = builder.onMarketEvent;
        this.finisher = builder.finisher;
    }

    public AlgorithmFactory<Algorithm> algorithmFactory() {
        return context -> new MarketScanAlgorithm<>(
                context,
                stateFactory,
                onBar,
                onMarketEvent,
                finisher,
                resultRef
        );
    }

    public Optional<R> result() {
        return Optional.ofNullable(resultRef.get());
    }

    public R resultOrDefault(R fallback) {
        R value = resultRef.get();
        return value == null ? fallback : value;
    }

    public static final class Builder<S, R> {
        private final StateFactory<S> stateFactory;
        private final Finisher<S, R> finisher;
        private OnBar<S> onBar;
        private OnMarketEvent<S> onMarketEvent;

        private Builder(StateFactory<S> stateFactory, Finisher<S, R> finisher) {
            this.stateFactory = Objects.requireNonNull(stateFactory, "stateFactory");
            this.finisher = Objects.requireNonNull(finisher, "finisher");
        }

        public Builder<S, R> onBar(OnBar<S> onBar) {
            this.onBar = onBar;
            return this;
        }

        public Builder<S, R> onMarketEvent(OnMarketEvent<S> onMarketEvent) {
            this.onMarketEvent = onMarketEvent;
            return this;
        }

        public MarketScan<S, R> build() {
            if (onBar == null && onMarketEvent == null)
                throw new IllegalArgumentException("onBar and onMarketEvent are both null");
            return new MarketScan<>(this);
        }
    }
}
