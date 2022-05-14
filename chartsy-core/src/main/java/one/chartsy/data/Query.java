/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

/**
 * Strategy for querying objects.
 *
 * @param <T> type of objects being queried
 * @param <R> query result type
 */
@FunctionalInterface
public interface Query<T, R> {

    R queryFrom(T obj);
}
