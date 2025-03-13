/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm.data;

import one.chartsy.messaging.MarketEvent;
import one.chartsy.time.DateChangeSignal;
import one.chartsy.trade.algorithm.Algorithm;
import one.chartsy.trade.algorithm.data.RankingStrategy.RankingOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Default implementation of the {@code InstrumentRanker} encapsulating ranking of instrument data
 * based on the provided strategy and order. Recalculates ranks lazily upon data changes,
 * delegating rank assignments to the provided post-processor.
 *
 * @param <I> type of instrument data managed by the ranker
 */
public class DefaultInstrumentRanker<I extends InstrumentData> implements InstrumentRanker<I> {

    private final Algorithm owner;
    private final RankingStrategy<I> strategy;
    private final RankingPostProcessor<I> postProcessor;
    private final RankingOrder order;
    private final DateChangeSignal dateChange = DateChangeSignal.create();
    private final Iterable<I> sourceInstruments;
    private final List<I> rankedInstruments = new ArrayList<>();
    private final List<I> rankedInstrumentsView = Collections.unmodifiableList(rankedInstruments);
    private boolean updated;

    /**
     * Creates a ranker instance linked to a specific algorithm, ranking strategy, post-processing logic, and order.
     *
     * @param owner         the {@code Algorithm} owning this ranker, providing contextual information for processing
     * @param instruments   the instruments available to the ranker
     * @param strategy      the {@code RankingStrategy} defining how scores are computed from instruments
     * @param postProcessor the {@code RankingPostProcessor} callback invoked after ranking to assign numeric ranks to instruments
     * @param order         the {@code RankingOrder} specifying sorting logic: higher scores better (descending) or lower better (ascending)
     */
    public DefaultInstrumentRanker(Algorithm owner,
                                   Iterable<I> instruments,
                                   RankingStrategy<I> strategy,
                                   RankingPostProcessor<I> postProcessor,
                                   RankingOrder order) {
        this.owner = Objects.requireNonNull(owner, "owner algorithm");
        this.strategy = Objects.requireNonNull(strategy, "ranking strategy");
        this.postProcessor = Objects.requireNonNull(postProcessor, "ranking post-processor");
        this.order = Objects.requireNonNull(order, "ranking order");
        this.sourceInstruments = Objects.requireNonNull(instruments, "instruments");
    }

    @Override
    public void onMarketMessage(MarketEvent message) {
        if (dateChange.poll(message)) {
            doRankInstruments();
        }
    }

    @Override
    public void rerankNow() {
        doRankInstruments();
    }

    protected void doRankInstruments() {
        rankedInstruments.clear();
        sourceInstruments.forEach(rankedInstruments::add);
        rankedInstruments.sort(strategy.comparator(order));
        updated = true;
        postProcessor.processAllRanks(rankedInstruments, owner);
    }

    @Override
    public boolean isUpdated() {
        if (updated) {
            updated = false;
            return true;
        }
        return false;
    }

    @Override
    public List<I> getRankedInstruments() {
        return rankedInstrumentsView;
    }
}