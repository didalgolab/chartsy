/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation.messaging;

import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.MarketMessageHandler;
import one.chartsy.simulation.time.PlaybackClock;

/**
 * A {@code MarketSupplier} wrapper that updates a {@code PlaybackClock} to the timestamp
 * of each event before forwarding the event to the supplied handler.
 *
 * @author Mariusz Bernacki
 */
public final class PlaybackMarketMessageHandler implements MarketMessageHandler {

    private final PlaybackClock clock;
    private final MarketMessageHandler downstream;

    public PlaybackMarketMessageHandler(PlaybackClock clock, MarketMessageHandler downstream) {
        this.clock = clock;
        this.downstream = downstream;
    }

    @Override
    public void onMarketMessage(MarketEvent event) {
        if (clock.time() != event.getTime())
            clock.setTime(event.getTime());
        downstream.onMarketMessage(event);
    }
}
