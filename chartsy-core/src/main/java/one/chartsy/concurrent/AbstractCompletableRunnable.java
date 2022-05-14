/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.concurrent;

import java.util.concurrent.CompletableFuture;

public abstract class AbstractCompletableRunnable<T> implements CompletableRunnable<T>, Runnable {

    private final CompletableFuture<T> future = new CompletableFuture<>();

    @Override
    public final void run() {
        try {
            run(future);
            future.complete(null);
        } catch (Throwable x) {
            future.completeExceptionally(x);
            logError(x);
        }
    }

    protected void logError(Throwable cause) {
        System.getLogger(getClass().getName()).log(System.Logger.Level.ERROR, "Run method finished with errors", cause);
    }

    public final CompletableFuture<T> getFuture() {
        return future;
    }
}
