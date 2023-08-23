/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.concurrent;

import java.util.concurrent.CompletableFuture;

/**
 * A runnable provided with a {@code CompletableFuture}.
 *
 * The implementation is responsible for completing the provided {@code CompletableFuture}.
 */
@FunctionalInterface
public interface CompletableRunnable<T> {

    void run(CompletableFuture<T> future);

}
