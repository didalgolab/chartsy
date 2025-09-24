/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.messaging;

import one.chartsy.time.Chronological;

/**
 * A market event that carries a typed, time-stamped payload.
 */
public interface MarketDataEvent<T extends Chronological> extends MarketEvent {

    /**
     * Returns the event payload (e.g., a Candle, Tick, Quote, etc.).
     */
    T get();

    @Override
    default long time() {
        return get().time();
    }
}