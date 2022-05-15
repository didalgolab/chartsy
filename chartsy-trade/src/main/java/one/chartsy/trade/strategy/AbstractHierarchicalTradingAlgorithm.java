/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.strategy;

import one.chartsy.When;
import one.chartsy.core.TriConsumer;
import one.chartsy.time.Chronological;
import one.chartsy.trade.data.Position;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class AbstractHierarchicalTradingAlgorithm extends AbstractTradingAlgorithm {

    /** Marks if the system is after init call. */
    private boolean isAfterInit;

    private final List<ChildFactory> childFactories = Collections.synchronizedList(new ArrayList<>());
    private ChildInvoker[] children = new ChildInvoker[0];

    private final List<TradingAlgorithm> allSubStrategies = new ArrayList<>();


    /**
     * Gives mark if the current trading algorithm is after init call.
     */
    public final boolean isAfterInit() {
        return isAfterInit;
    }

    @Override
    public void onInit(TradingAlgorithmContext context) {
        super.onInit(context);
    }

    @Override
    public void onAfterInit() {
        super.onAfterInit();
        isAfterInit = true;
        invokeAll(getAllSubStrategies(), TradingAlgorithm::onAfterInit);
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

            List<TradingAlgorithm> newlyCreated = new LinkedList<>();
            for (ChildFactory factory : childFactories) {
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

    public void addSubStrategies(TradingAlgorithmFactory<?> factory, Function<When, Object> partitionFunction) {
        var childFactory = new ChildFactory(factory, partitionFunction);
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



    private final class ChildFactory {
        private final Function<When, Object> partitionFunction;
        private final TradingAlgorithmFactory<?> factory;
        private final Map<Object, TradingAlgorithm> algorithmPartitions = new ConcurrentHashMap<>();
        private final Lock lock = new ReentrantLock();

        ChildFactory(TradingAlgorithmFactory<?> factory, Function<When, Object> partitionFunction) {
            this.partitionFunction = partitionFunction;
            this.factory = factory;
        }

        TradingAlgorithm getTargetAlgorithm(TradingAlgorithmContext context, When when, List<TradingAlgorithm> newlyCreated) {
            Object partition = partitionFunction.apply(when);
            if (partition == null)
                return null;

            TradingAlgorithm algorithm = algorithmPartitions.get(partition);
            if (algorithm == null)
                algorithm = getOrCreateTargetAlgorithm(context, partition, newlyCreated);
            return algorithm;
        }

        TradingAlgorithm getOrCreateTargetAlgorithm(TradingAlgorithmContext context, Object partition, List<TradingAlgorithm> newlyCreated) {
            lock.lock();
            try {
                var algorithm = algorithmPartitions.get(partition);
                if (algorithm == null) {
                    algorithm = createTargetAlgorithm(context, partition);
                    newlyCreated.add(algorithm);
                    algorithmPartitions.put(partition, algorithm);
                }
                return algorithm;
            } finally {
                lock.unlock();
            }
        }

        TradingAlgorithm createTargetAlgorithm(TradingAlgorithmContext context, Object partitionKey) {
            var algorithmName = context.name().isBlank()? String.valueOf(partitionKey) : context.name() + "." + partitionKey;
            var algorithmContext = ImmutableTradingAlgorithmContext.builder()
                    .from(context)
                    .strategyPartition(partitionKey)
                    .name(algorithmName)
                    .build();
            return context.tradingAlgorithms().newInstance(algorithmName, algorithmContext, factory);
        }
    }

    private record ChildInvoker(
            When when,
            List<TradingAlgorithm> algorithms) {

        void addTarget(TradingAlgorithm algorithm) {
            algorithms.add(algorithm);
        }
    }

    public AbstractHierarchicalTradingAlgorithm() { }

    public AbstractHierarchicalTradingAlgorithm(TradingAlgorithmContext context) {
        super(context);
    }

    public AbstractHierarchicalTradingAlgorithm(TradingAlgorithmContext context, Map<String, ?> parameters) {
        super(context, parameters);
    }
}
