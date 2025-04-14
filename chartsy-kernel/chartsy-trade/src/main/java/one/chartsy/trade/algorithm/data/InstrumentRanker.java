/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm.data;

import one.chartsy.messaging.MarketEvent;

import java.util.List;
import java.util.stream.Stream;

/**
 * Defines a contract for ranking a collection of {@link InstrumentData} instances according
 * to a dynamic, strategy-driven order.
 * <p>
 * An {@code InstrumentRanker} maintains an up-to-date, ordered view of instruments,
 * typically for use in algorithmic trading where selection, prioritization, or filtering of
 * instruments is required. Implementations are expected to respond to market events and
 * other triggers, recalculating ranks as necessary and exposing the current ranking to clients.
 * <p>
 * The ranking process is typically delegated to a {@link RankingStrategy} and may be further
 * absorbed by a {@link RankingPostProcessor}. The ranker is designed to be integrated into
 * event-driven trading systems, reacting to {@link MarketEvent}s and supporting both automatic
 * and manual re-ranking.
 *
 * @param <T> the type of instrument data being ranked
 *
 * @see InstrumentData
 * @see RankingStrategy
 * @see RankingPostProcessor
 */
public interface InstrumentRanker<T extends InstrumentData> {

    /**
     * Notifies the ranker of a new market event, which may trigger a re-ranking of instruments.
     *
     * @param message the market event to process
     */
    void onMarketMessage(MarketEvent message);

    /**
     * Forces an immediate on-demand re-ranking of all managed instruments.
     */
    void rerankNow();

    /**
     * Indicates whether the ranking has been updated since the last check.
     * This method is designed to be used by consumers to detect changes in the ranking
     * and react accordingly. <b>The flag is cleared after this method is called.</b>
     *
     * @return {@code true} if the ranking was updated since the last invocation; {@code false} otherwise
     */
    boolean isUpdated();

    /**
     * Returns an immutable, ordered list of the currently ranked instruments.
     *
     * @return an unmodifiable list of ranked instruments
     */
    List<T> getRankedInstruments();

    /**
     * Returns a sequential stream of the currently ranked instruments.
     *
     * @return a stream of ranked instruments
     */
    default Stream<T> rankedInstruments() {
        return getRankedInstruments().stream();
    }
}