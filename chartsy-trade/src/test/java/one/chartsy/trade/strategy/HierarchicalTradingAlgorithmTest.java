/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.strategy;

import one.chartsy.SymbolIdentity;
import one.chartsy.When;
import one.chartsy.data.Series;
import one.chartsy.data.structures.IntHashMap;
import one.chartsy.data.structures.IntMap;
import one.chartsy.naming.SymbolIdentifier;
import one.chartsy.time.Chronological;
import one.chartsy.trade.data.Position;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mockito;

import static one.chartsy.data.Series.PARTITION_BY_SYMBOL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;

class HierarchicalTradingAlgorithmTest {

    TradingAlgorithmFactory<?> subStrategies = ctx -> Mockito.spy(new MyAlgorithm(ctx));
    TradingAlgorithmContext context = testContext();
    HierarchicalTradingAlgorithm algorithm = new HierarchicalTradingAlgorithm(context) { };

    SymbolIdentifier SYMBOL = new SymbolIdentifier("SYMBOL");
    SymbolIdentifier SYMBOL_1 = new SymbolIdentifier("SYMBOL_1");
    SymbolIdentifier SYMBOL_2 = new SymbolIdentifier("SYMBOL_2");

    @Test
    void has_no_subStrategies_when_newly_created() {
        algorithm.addSubStrategies(subStrategies, PARTITION_BY_SYMBOL);
        assertThat(algorithm.getAllSubStrategies()).isEmpty();

        algorithm.onInit(new HostTradingAlgorithmContext());
        algorithm.onAfterInit();
        assertThat(algorithm.getAllSubStrategies()).isEmpty();
    }

    private When when(int id, SymbolIdentity symbol) {
        When when = Mockito.mock(When.class);
        Mockito.when(when.getId()).thenReturn(id);
        Mockito.when(when.getSymbol()).thenReturn(symbol);
        Mockito.when(context.partitionSeries().get(id).getSymbol()).thenReturn(symbol);
        return when;
    }

    @Test
    void addSubStrategies_creates_a_subStrategy_when_data_starts_flowing() {
        algorithm.addSubStrategies(subStrategies, PARTITION_BY_SYMBOL);
        algorithm.onInit(new HostTradingAlgorithmContext());
        algorithm.onAfterInit();

        When when1 = when(1, SYMBOL);
        algorithm.entryOrders(when1, null);

        assertThat( algorithm.getAllSubStrategies() ).hasSize(1);
        var subStrategy = algorithm.getAllSubStrategies().get(0);
        InOrder inOrder = Mockito.inOrder(subStrategy);
        inOrder.verify(subStrategy).onInit(ArgumentMatchers.any());
        inOrder.verify(subStrategy).onAfterInit();
        inOrder.verify(subStrategy).entryOrders(when1, null);

        When when2 = when(2, SYMBOL);
        algorithm.entryOrders(when2, null);
        algorithm.entryOrders(when1, null);

        assertThat( algorithm.getAllSubStrategies() ).hasSize(1);
        inOrder.verify(subStrategy).entryOrders(when2, null);
        inOrder.verify(subStrategy).entryOrders(when1, null);
    }

    @Test
    void addSubStrategies_creates_as_many_subStrategies_as_symbols_in_series_if_PARTITIONED_BY_SYMBOL() {
        algorithm.addSubStrategies(subStrategies, PARTITION_BY_SYMBOL);
        algorithm.onInit(new HostTradingAlgorithmContext());
        algorithm.onAfterInit();

        When when1 = when(1, SYMBOL_1);
        algorithm.entryOrders(when1, null);

        When when2 = when(2, SYMBOL_2);
        algorithm.entryOrders(when2, null);

        int n = 0;
        assertThat( algorithm.getAllSubStrategies() ).hasSize(2);
        for (var subStrategy : algorithm.getAllSubStrategies()) {
            InOrder inOrder = Mockito.inOrder(subStrategy);
            inOrder.verify(subStrategy).onInit(ArgumentMatchers.any());
            inOrder.verify(subStrategy).onAfterInit();
            inOrder.verify(subStrategy).entryOrders(((++n == 1)? when1: when2), null);
        }
    }

    @Test
    void addSubStrategies_creates_new_subStrategies_immediately_for_all_already_existing_data_series() {
        algorithm.onInit(new HostTradingAlgorithmContext());
        algorithm.onAfterInit();

        When when1 = when(1, SYMBOL);
        algorithm.entryOrders(when1, null);

        algorithm.addSubStrategies(subStrategies, PARTITION_BY_SYMBOL);

        assertThat( algorithm.getAllSubStrategies() ).hasSize(1);
        var subStrategy = algorithm.getAllSubStrategies().get(0);
        InOrder inOrder = Mockito.inOrder(subStrategy);
        inOrder.verify(subStrategy).onInit(ArgumentMatchers.any());
        inOrder.verify(subStrategy).onAfterInit();
        inOrder.verify(subStrategy, never()).entryOrders(when1, null);

        algorithm.entryOrders(when1, null);
        inOrder.verify(subStrategy, Mockito.times(1)).entryOrders(when1, null);
    }

    static class MyAlgorithm extends AbstractTradingAlgorithm {
        @Override
        public void exitOrders(When when, Position position) { }

        @Override
        public void entryOrders(When when, Chronological data) { }

        MyAlgorithm(TradingAlgorithmContext context) {
            super(context);
        }
    }

    static TradingAlgorithmContext testContext() {
        IntMap<Series<?>> seriesMap = new IntHashMap<>();
        for (int i = 0; i < 1000; i++)
            seriesMap.put(i, Mockito.mock(Series.class));

        return ImmutableTradingAlgorithmContext.builder()
                .from(new HostTradingAlgorithmContext())
                .tradingAlgorithms(new DefaultTradingAlgorithmSet())
                .partitionSeries(seriesMap)
                .build();
    }
}