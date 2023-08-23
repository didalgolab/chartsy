/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.strategy;

import one.chartsy.util.Pair;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class StandardTradingAlgorithmSet implements TradingAlgorithmSet {

    /** The map of trading algorithms by name. */
    private final Map<String, TradingAlgorithm> algorithms = new ConcurrentHashMap<>();


    @Override
    public Collection<TradingAlgorithm> findAll() {
        return algorithms.values();
    }

    @Override
    public Optional<TradingAlgorithm> get(String name) {
        return Optional.ofNullable(algorithms.get(name));
    }

    public boolean contains(String name) {
        return algorithms.containsKey(name);
    }

    private static Pair<String, Long> splitByHashNumber(String name) {
        for (int i = name.length(); --i >= 0; ) {
            var ch = name.charAt(i);
            if (ch == '#' && i < name.length() - 1)
                return Pair.of(name.substring(0, i), Long.parseLong(name, i+1, name.length(), 10));
            if (ch < '0' || ch > '9')
                break;
        }
        return Pair.of(name, null);
    }

    @Override
    public <T extends TradingAlgorithm> T newInstance(String name, TradingAlgorithmContext context, TradingAlgorithmFactory<T> factory) {
        var nameAndHash = splitByHashNumber(name);
        if (nameAndHash.getRight() != null) {
            long hashNumber = nameAndHash.getRight();
            while (contains(name)) {
                hashNumber = Math.addExact(hashNumber, 1L);
                name = nameAndHash.getLeft() + '#' + hashNumber;
            }
        }

        var algorithmContext = ImmutableTradingAlgorithmContext.builder()
                .from(context)
                .name(name)
                .build();
        var algorithm = factory.create(algorithmContext);
        algorithms.put(name, algorithm);
        return algorithm;
    }
}
