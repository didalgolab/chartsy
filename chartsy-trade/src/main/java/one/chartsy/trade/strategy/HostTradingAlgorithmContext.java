/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.strategy;

import one.chartsy.collections.ImmutableCollections;
import one.chartsy.data.Series;
import one.chartsy.data.structures.IntHashMap;
import one.chartsy.data.structures.IntMap;
import one.chartsy.data.structures.UnmodifiableIntMap;
import one.chartsy.scheduling.EventScheduler;
import one.chartsy.time.Clock;
import one.chartsy.trade.*;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@ServiceProvider(service = TradingAlgorithmContext.class)
public class HostTradingAlgorithmContext implements TradingAlgorithmContext {

    private final Clock hostClock = Clock.systemDefaultZone();
    private final EventScheduler scheduler = new Scheduler();

    @Override
    public String name() {
        return "$";
    }

    @Override
    public Clock clock() {
        return hostClock;
    }

    @Override
    public EventScheduler scheduler() {
        return scheduler;
    }

    @Override
    public TradingService tradingService() {
        return FakeTradingService.INSTANCE;
    }

    @Override
    public Optional<Object> partitionKey() {
        return Optional.empty();
    }

    @Override
    public List<Object> partitionKeys() {
        return List.of();
    }

    @Override
    public IntMap<Series<?>> partitionSeries() {
        return UnmodifiableIntMap.of(new IntHashMap<>(0));
    }

    @Override
    public ConcurrentMap<String, Object> sharedVariables() {
        return ImmutableCollections.emptyConcurrentMap();
    }

    @Override
    public TradingAlgorithmSet tradingAlgorithms() {
        return FakeTradingAlgorithmSet.INSTANCE;
    }

    @Override
    public TradingOptions options() {
        return TradingOptions.getDefault();
    }

    @Override
    public StrategyConfiguration configuration() {
        return StrategyConfiguration.builder().build();
    }

    @Override
    public Lookup getLookup() {
        return Lookup.getDefault();
    }

    /**
     * Class-private scheduler.
     */
    final class Scheduler implements EventScheduler {
        @Override
        public void schedule(long triggerTime, Runnable event) {
            long delay = triggerTime - hostClock.time();
            executor.schedule(event, delay, TimeUnit.MICROSECONDS);
        }

        static final class DaemonThreadFactory implements ThreadFactory {
            public Thread newThread(Runnable r) {
                var t = new Thread(r);
                t.setDaemon(true);
                t.setName("HostTradingAgentRuntimeScheduler");
                return t;
            }
        }

        static final ScheduledThreadPoolExecutor executor;
        static {
            (executor = new ScheduledThreadPoolExecutor(
                    1, new DaemonThreadFactory())).
                    setRemoveOnCancelPolicy(true);
        }
    }

    private static class FakeTradingService implements TradingService {
        private static final FakeTradingService INSTANCE = new FakeTradingService();

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

    private static class FakeTradingAlgorithmSet implements TradingAlgorithmSet {
        private static final FakeTradingAlgorithmSet INSTANCE = new FakeTradingAlgorithmSet();

        @Override
        public Collection<TradingAlgorithm> findAll() {
            return List.of();
        }

        @Override
        public Optional<TradingAlgorithm> get(String name) {
            return Optional.empty();
        }

        @Override
        public <T extends TradingAlgorithm> T newInstance(String name, TradingAlgorithmContext context, TradingAlgorithmFactory<T> factory) {
            throw new UnsupportedOperationException("Unsupported by HostTradingAlgorithmContext");
        }
    }
}
