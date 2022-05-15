/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.core;

/**
 * An operation that accepts three input arguments and returns no result.
 * This is the three-arity specialization of a {@code Consumer}.
 *
 * @param <K> type of the first argument
 * @param <V> type of the second argument
 * @param <S> type of the third argument
 */
@FunctionalInterface
public interface TriConsumer<K, V, S> {

    /**
     * Performs the operation given the specified arguments.
     *
     * @param k the first input argument
     * @param v the second input argument
     * @param s the third input argument
     */
    void accept(K k, V v, S s);
}