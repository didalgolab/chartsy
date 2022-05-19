/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation.services;

import one.chartsy.data.Series;
import one.chartsy.data.structures.IntMap;
import one.chartsy.simulation.ImmutableSimulationContext;
import one.chartsy.simulation.SimulationContext;
import one.chartsy.simulation.BacktestConfiguration;
import one.chartsy.trade.Account;
import one.chartsy.trade.OrderBroker;
import one.chartsy.trade.TradingService;
import one.chartsy.trade.strategy.HostTradingAlgorithmContext;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

import java.util.List;

@ServiceProvider(service = SimulationContext.class)
public class HostSimulationContext extends HostTradingAlgorithmContext implements SimulationContext {

    @Override
    public Lookup getLookup() {
        return Lookup.EMPTY;
    }

    @Override
    public BacktestConfiguration configuration() {
        return BacktestConfiguration.builder().build();
    }

    @Override
    public TradingService tradingService() {
        return FakeTradingService.instance;
    }

    @Override
    public SimulationContext withPartitionSeries(IntMap<Series<?>> ps) {
        return ImmutableSimulationContext.builder()
                .from(this)
                .partitionSeries(ps)
                .build();
    }

    private static class FakeTradingService implements TradingService {
        private static final FakeTradingService instance = new FakeTradingService();

        @Override
        public OrderBroker getOrderBroker() {
            throw new UnsupportedOperationException("OrderBroker");
        }

        @Override
        public List<Account> getAccounts() {
            return List.of();
        }

        @Override
        public Lookup getLookup() {
            return Lookup.EMPTY;
        }
    }
}
