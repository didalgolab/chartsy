package one.chartsy.concurrent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;

public abstract class AbstractCompletableRunnable<T> implements CompletableRunnable<T>, Runnable {

    private final CompletableFuture<T> future = new CompletableFuture<>();
    private final Logger log = LogManager.getLogger(getClass());

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
        log.error("Run finished with error", cause);
    }

    public final CompletableFuture<T> getFuture() {
        return future;
    }
}
