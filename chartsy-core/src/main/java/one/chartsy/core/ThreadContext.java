package one.chartsy.core;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

public class ThreadContext {

    private static final ThreadLocal<ThreadContext> current = new ThreadLocal<>();
    private final Map<?, ?> vars;


    public static ThreadContext current() {
        ThreadContext context = current.get();
        if (context == null)
            throw new NotFoundException("Current ThreadContext not captured");
        return context;
    }

    public static ThreadContext empty() {
        return of(Collections.emptyMap());
    }

    public static ThreadContext of(Map<?, ?> vars) {
        return new ThreadContext(vars);
    }

    protected ThreadContext(Map<?, ?> vars) {
        Objects.requireNonNull(vars, "vars");
        this.vars = vars;
    }

    public Map<?, ?> getVars() {
        return vars;
    }

    public <V> Callable<V> contextualCallable(Callable<V> target) {
        class ContextualCallable implements Callable<V> {
            @Override
            public V call() throws Exception {
                ThreadContext curr = ThreadContext.this;
                ThreadContext prev = current.get();
                try {
                    current.set(curr);
                    return target.call();
                } finally {
                    current.set(prev);
                }
            }
        }
        return new ContextualCallable();
    }

    public Runnable contextualRunnable(Runnable target) {
        class ContextualRunnable implements Runnable {
            @Override
            public void run() {
                ThreadContext curr = ThreadContext.this;
                ThreadContext prev = current.get();
                try {
                    current.set(curr);
                    target.run();
                } finally {
                    current.set(prev);
                }
            }
        }
        return new ContextualRunnable();
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }
}
