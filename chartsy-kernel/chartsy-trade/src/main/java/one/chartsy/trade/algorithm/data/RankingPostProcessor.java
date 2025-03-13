/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm.data;

import one.chartsy.trade.algorithm.Algorithm;

import java.util.List;

/**
 * A callback interface for processing rank assignments on instrument data.
 * It allows assigning numerical ranks (e.g., 1, 2, 3, etc.) to instances of {@link InstrumentData}
 * after a ranking or sorting mechanism has determined the order.
 *
 * <p>This interface is generic, parameterized with the instrument data type, and fits well for
 * passing as a lambda callback.
 *
 * @param <I> the type of {@link InstrumentData} that this processor will handle
 */
@FunctionalInterface
public interface RankingPostProcessor<I extends InstrumentData> {

    /** The no-op post processor which simply does nothing on rank assignment. */
    RankingPostProcessor<?> NOOP = (instr, rank) -> { };

    /**
     * Processes the rank assignment for a single instrument.
     *
     * @param instr   the instrument data for which the rank is being assigned
     * @param newRank the new rank to assign to the instrument (e.g., 1 for the top-ranked instrument)
     */
    void processRank(I instr, int newRank);

    /**
     * Processes the rank assignment for a list of ranked instruments.
     * <p>
     * This default implementation iterates through the provided list of instruments, which are assumed to be
     * sorted in the desired order, and assigns increasing rank numbers starting from 1.
     * </p>
     *
     * @param rankedInstruments a list of instruments sorted by their ranking criteria
     * @param sourceAlgorithm   the algorithm that produced the ranking; this parameter can be used for
     *                          logging or additional processing, though it is not used in the default implementation
     */
    default void processAllRanks(List<I> rankedInstruments, Algorithm sourceAlgorithm) {
        int rank = 1;
        for (I instr : rankedInstruments) {
            processRank(instr, rank++);
        }
    }

    /**
     * Returns a no-operation {@code RankingPostProcessor} which simply does nothing on rank assignment.
     *
     * @param <T> the type of {@link InstrumentData} handled by the processor
     * @return a no-op processor
     */
    @SuppressWarnings("unchecked")
    static <T extends InstrumentData> RankingPostProcessor<T> noop() {
        return (RankingPostProcessor<T>) NOOP;
    }
}
