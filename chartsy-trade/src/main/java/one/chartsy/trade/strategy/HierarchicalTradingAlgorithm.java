/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.strategy;

import one.chartsy.When;
import one.chartsy.data.function.TriConsumer;
import one.chartsy.data.Series;
import one.chartsy.data.structures.IntHashMap;
import one.chartsy.data.structures.IntMap;
import one.chartsy.data.structures.UnmodifiableIntMap;
import one.chartsy.time.Chronological;
import one.chartsy.trade.MarketUniverse;
import one.chartsy.trade.MarketUniverseChangeEvent;
import one.chartsy.trade.MarketUniverseChangeListener;
import one.chartsy.trade.MarketUniverseObserver;
import one.chartsy.trade.data.Position;
import one.chartsy.util.ListUtils;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class HierarchicalTradingAlgorithm extends AbstractTradingAlgorithm {

    /** Marks if the system is after init call. */
    private boolean isAfterInit;

    private final MarketUniverseObserver marketUniverse = new MarketUniverseObserver();

    private final MarketUniverseChangeListener marketUniverseHandler = (MarketUniverseChangeEvent event) -> {
        invokeAll(getAllSubStrategies(), TradingAlgorithm::onMarketUniverseChange, event);
    };

    private final List<TradingSystem> childFactories = Collections.synchronizedList(new ArrayList<>());
    private ChildInvoker[] children = new ChildInvoker[0];

    private final List<TradingAlgorithm> allSubStrategies = new ArrayList<>();


    /**
     * Gives the view of a market universe captured by this trading algorithm.
     *
     * @return the readonly view of a market universe
     */
    public MarketUniverse getMarketUniverse() {
        return marketUniverse;
    }

    /**
     * Gives mark if the current trading algorithm is after init call.
     */
    public final boolean isAfterInit() {
        return isAfterInit;
    }

    @Override
    public void onInit(TradingAlgorithmContext context) {
        super.onInit(context);
        marketUniverse.removeListener(marketUniverseHandler);
        marketUniverse.addListener(marketUniverseHandler);
    }

    @Override
    public void onAfterInit() {
        super.onAfterInit();
        isAfterInit = true;
        invokeAll(getAllSubStrategies(), TradingAlgorithm::onAfterInit);
    }

    @Override
    public void onMarketUniverseChange(MarketUniverseChangeEvent change) {
        super.onMarketUniverseChange(change);
        if (change.hasRemovedMarkets())
            change.getRemovedMarkets().forEach(marketUniverse::removeMarket);
    }

    @Override
    public void onExit(ExitState state) {
        super.onExit(state);
        invokeAll(getAllSubStrategies(), TradingAlgorithm::onExit, state);
    }

    @Override
    public void onTradingDayStart(LocalDate date) {
        invokeAll(getAllSubStrategies(), TradingAlgorithm::onTradingDayStart, date);
    }

    @Override
    public void onTradingDayEnd(LocalDate date) {
        invokeAll(getAllSubStrategies(), TradingAlgorithm::onTradingDayEnd, date);
    }

    @Override
    public void doFirst(When when) {
        invokeAll(getSubStrategies(when), TradingAlgorithm::doFirst, when);
    }

    @Override
    public void exitOrders(When when, Position position) {
        invokeAll(getSubStrategies(when), TradingAlgorithm::exitOrders, when, position);
    }

    @Override
    public void entryOrders(When when, Chronological data) {
        invokeAll(getSubStrategies(when), TradingAlgorithm::entryOrders, when, data);
    }

    @Override
    public void doLast(When when) {
        invokeAll(getSubStrategies(when), TradingAlgorithm::doLast, when);
    }

    @Override
    public void onData(When when, Chronological next, boolean timeTick) {
        List<TradingAlgorithm> subStrategies = getAllSubStrategies();
        for (int i = 0, count = subStrategies.size(); i < count; i++)
            subStrategies.get(i).onData(when, next, timeTick);
    }

    /**
     * Returns an unmodifiable view of all sub-strategies contained by the trading algorithm.
     */
    public List<TradingAlgorithm> getAllSubStrategies() {
        return Collections.unmodifiableList(allSubStrategies);
    }

    public List<TradingAlgorithm> getSubStrategies(When when) {
        return getInvoker(when).algorithms();
    }

    protected ChildInvoker getInvoker(When when) {
        int id = when.getId();
        if (id >= children.length)
            children = Arrays.copyOf(children, Math.max(id + 1, children.length * 2));

        var invoker = children[id];
        if (invoker == null) {
            invoker = children[id] = new ChildInvoker(when, new ArrayList<>());
            marketUniverse.addMarket(when);

            List<TradingAlgorithm> newlyCreated = new LinkedList<>();
            for (TradingSystem factory : childFactories) {
                var targetAlgorithm = factory.getTargetAlgorithm(context, when, newlyCreated);
                if (targetAlgorithm != null)
                    invoker.addTarget(targetAlgorithm);
            }

            if (!newlyCreated.isEmpty()) {
                allSubStrategies.addAll(newlyCreated);
                invokeAll(newlyCreated, TradingAlgorithm::onInit, context);
                if (isAfterInit())
                    invokeAll(newlyCreated, TradingAlgorithm::onAfterInit);
            }
        }
        return invoker;
    }

    public void addSubStrategies(TradingAlgorithmFactory<?> factory) {
        addSubStrategies(factory, Series.PARTITION_BY_SYMBOL);
    }

    public void addSubStrategies(TradingAlgorithmFactory<?> factory, Function<Series<?>, ?> partitionFunction) {
        var childFactory = new TradingSystem(factory, partitionFunction);
        childFactories.add(childFactory);

        List<TradingAlgorithm> newlyCreated = new LinkedList<>();
        for (ChildInvoker slot : children) {
            if (slot != null) {
                var targetAlgorithm = childFactory.getTargetAlgorithm(context, slot.when(), newlyCreated);
                if (targetAlgorithm != null)
                    slot.addTarget(targetAlgorithm);
            }
        }

        allSubStrategies.addAll(newlyCreated);
        invokeAll(newlyCreated, TradingAlgorithm::onInit, context);
        if (isAfterInit())
            invokeAll(newlyCreated, TradingAlgorithm::onAfterInit);
    }

    protected void invokeAll(List<TradingAlgorithm> targets, Consumer<? super TradingAlgorithm> method) {
        targets.forEach(method);
    }

    protected <P> void invokeAll(List<TradingAlgorithm> targets, BiConsumer<? super TradingAlgorithm, P> method, P arg1) {
        for (var target : targets)
            method.accept(target, arg1);
    }

    protected <P1,P2> void invokeAll(List<TradingAlgorithm> targets, TriConsumer<? super TradingAlgorithm, P1, P2> method, P1 arg1, P2 arg2) {
        for (var target : targets)
            method.accept(target, arg1, arg2);
    }

    protected Lookup createChildLookup(TradingAlgorithmContext context, ConcurrentMap<String, ?> sharedVariables) {
        return new ProxyLookup(
                Lookups.fixed(sharedVariables, this),
                context.getLookup()
        );
    }

    private record Partition(TradingAlgorithm algorithm, IntMap<? super Series<?>> seriesView) { }

    private final class TradingSystem {
        private final Function<Series<?>, ?> partitionFunction;
        private final TradingAlgorithmFactory<?> factory;
        private final ConcurrentMap<String, Object> sharedVariables = new ConcurrentHashMap<>();
        private final Map<Object, Partition> algorithmPartitions = new ConcurrentHashMap<>();
        private final Lock lock = new ReentrantLock();

        TradingSystem(TradingAlgorithmFactory<?> factory, Function<Series<?>, ?> partitionFunction) {
            this.partitionFunction = partitionFunction;
            this.factory = factory;
        }

        TradingAlgorithm getTargetAlgorithm(TradingAlgorithmContext context, When when, List<TradingAlgorithm> newlyCreated) {
            Series<?> series = context.partitionSeries().get(when.getId());
            Object partitionKey = partitionFunction.apply(series);
            if (partitionKey == null)
                return null;

            var partition = algorithmPartitions.get(partitionKey);
            if (partition == null)
                partition = getOrCreatePartition(context, partitionKey, newlyCreated);

            partition.seriesView.put(when.getId(), context.partitionSeries().get(when.getId()));
            return partition.algorithm;
        }

        Partition getOrCreatePartition(TradingAlgorithmContext context, Object partitionKey, List<TradingAlgorithm> newlyCreated) {
            lock.lock();
            try {
                var partition = algorithmPartitions.get(partitionKey);
                if (partition == null) {
                    partition = createTargetPartition(context, partitionKey, createChildLookup(context, sharedVariables));
                    newlyCreated.add(partition.algorithm);
                    algorithmPartitions.put(partitionKey, partition);
                }
                return partition;
            } finally {
                lock.unlock();
            }
        }

        Partition createTargetPartition(TradingAlgorithmContext context, Object partitionKey, Lookup lookup) {
            String algorithmName = context.name().isBlank()? String.valueOf(partitionKey) : context.name() + "." + partitionKey;

            IntMap<Series<?>> partitionSeries = new IntHashMap<>(4);
            context.partitionSeries().forEach((seriesId, series) -> {
                if (partitionKey.equals(partitionFunction.apply(series)))
                    partitionSeries.put(seriesId, series);
            });
            TradingAlgorithmContext algorithmContext = ImmutableTradingAlgorithmContext.builder()
                    .from(context)
                    .lookup(lookup)
                    .partitionKey(partitionKey)
                    .partitionKeys(ListUtils.appendTo(context.partitionKeys(), partitionKey))
                    .partitionSeries(UnmodifiableIntMap.of(partitionSeries))
                    .sharedVariables(sharedVariables)
                    .name(algorithmName)
                    .build();
            TradingAlgorithm algorithm = context.tradingAlgorithms().newInstance(algorithmName, algorithmContext, factory);
            return new Partition(algorithm, partitionSeries);
        }
    }

    private record ChildInvoker(
            When when,
            List<TradingAlgorithm> algorithms) {

        void addTarget(TradingAlgorithm algorithm) {
            algorithms.add(algorithm);
        }
    }

    public HierarchicalTradingAlgorithm(TradingAlgorithmContext context) {
        super(context);
    }

    public HierarchicalTradingAlgorithm(TradingAlgorithmContext context, Map<String, ?> parameters) {
        super(context, parameters);
    }
}
