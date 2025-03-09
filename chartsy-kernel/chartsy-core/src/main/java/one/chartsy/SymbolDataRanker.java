/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.function.ToDoubleFunction;

/**
 * Manages ranking of {@link SymbolData} instances based on their ranking values for long and short trades.
 *
 * @param <T> the type of custom data associated with symbols
 */
public class SymbolDataRanker<T> {

    private final Map<SymbolIdentity, SymbolData<T>> symbolData = new HashMap<>();
    private final List<SymbolData<T>> longRanks = new ArrayList<>();
    private final List<SymbolData<T>> shortRanks = new ArrayList<>();
    private final List<SymbolData<T>> longRanksView = Collections.unmodifiableList(longRanks);
    private final List<SymbolData<T>> shortRanksView = Collections.unmodifiableList(shortRanks);

    /**
     * Retrieves or creates {@link SymbolData} for the given symbol. If the associated custom data implements
     * {@link SymbolDataAware}, it receives a callback with the created {@code SymbolData} instance.
     *
     * @param symbol the symbol identity
     * @param customDataFunction function to generate custom data for the symbol
     * @return the associated {@code SymbolData} instance, never {@code null}
     */
    @SuppressWarnings("unchecked")
    public SymbolData<T> getSymbolData(SymbolIdentity symbol, Function<SymbolIdentity, T> customDataFunction) {
        return symbolData.computeIfAbsent(symbol, symbolId -> {
            var customData = customDataFunction.apply(symbolId);
            var symbolData = new SymbolData<>(symbolId, customData);
            if (customData instanceof SymbolDataAware<?>)
                ((SymbolDataAware<T>) customData).setSymbolData(symbolData);

            longRanks.add(symbolData);
            shortRanks.add(symbolData);
            return symbolData;
        });
    }

    /**
     * Returns an unmodifiable view of the long ranks.
     *
     * @return list of symbol data ranked for long trades starting from symbols with the
     * highest ranking values to the lowest
     */
    public final List<SymbolData<T>> getLongRanks() {
        return longRanksView;
    }

    /**
     * Returns an unmodifiable view of the short ranks.
     *
     * @return list of symbol data ranked for short trades starting from the symbols with
     * the lowest ranking values to the highest
     */
    public final List<SymbolData<T>> getShortRanks() {
        return shortRanksView;
    }

    /**
     * Ranks the instruments based on their long and short ranking values.
     * The instruments are sorted in descending order for long ranking values
     * and ascending order for short ranking values.
     * The rank of each instrument is then updated based on its position in the sorted list.
     */
    public void rankInstruments() {
        longRanks.sort(Comparator.comparingDouble((SymbolData<T> data) -> data.longRankingValue()).reversed());
        for (int i = 0; i < longRanks.size(); i++) {
            longRanks.get(i).setLongRank(i + 1);
        }

        shortRanks.sort(Comparator.comparingDouble(SymbolData::shortRankingValue));
        for (int i = 0; i < shortRanks.size(); i++) {
            shortRanks.get(i).setShortRank(i + 1);
        }
    }

    /**
     * Ranks instruments using a user-defined ranking algorithm.
     *
     * @param ranks the list to rank, sorted in-place
     * @param rankingFunction computes ranking values for each symbol
     * @param rankMemoizer assigns computed ranks to symbols
     * @param higherIsBetter indicates the sorting order: if {@code true} symbols with higher
     *                       ranking values appear earlier in the list and are assigned numerically
     *                       lower value of the rank (i.e.: the top symbol has rank equal to {@code 1});
     *                       if {@code false}, otherwise
     */
    public void rankInstruments(
            List<SymbolData<T>> ranks,
            ToDoubleFunction<SymbolData<T>> rankingFunction,
            ObjIntConsumer<SymbolData<T>> rankMemoizer,
            boolean higherIsBetter) {

        var comparator = Comparator.comparingDouble(rankingFunction);
        ranks.sort(higherIsBetter ? comparator.reversed() : comparator);
        for (int i = 0; i < ranks.size(); i++)
            rankMemoizer.accept(ranks.get(i), i + 1);
    }
}
