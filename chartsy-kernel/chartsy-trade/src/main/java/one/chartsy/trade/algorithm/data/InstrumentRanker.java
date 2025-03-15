/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm.data;

import one.chartsy.messaging.MarketEvent;

import java.util.List;
import java.util.stream.Stream;

public interface InstrumentRanker<T extends InstrumentData> {

    void onMarketMessage(MarketEvent message);

    /**
     * Re-ranks the available instruments.
     */
    void rerankNow();

    boolean isUpdated();

    List<T> getRankedInstruments();

    default Stream<T> rankedInstruments() {
        return getRankedInstruments().stream();
    }
}
