/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
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
                return execute(target::call);
            }
        }
        return new ContextualCallable();
    }

    public Runnable contextualRunnable(Runnable target) {
        class ContextualRunnable implements Runnable {
            @Override
            public void run() {
                execute(target::run);
            }
        }
        return new ContextualRunnable();
    }

    public <V, T extends Throwable> V execute(ThrowingSupplier<V,T> target) throws T {
        ThreadContext curr = ThreadContext.this;
        ThreadContext prev = current.get();
        try {
            current.set(curr);
            return target.get();
        } finally {
            current.set(prev);
        }
    }

    public <T extends Throwable> void execute(ThrowingRunnable<T> target) throws T {
        ThreadContext curr = ThreadContext.this;
        ThreadContext prev = current.get();
        try {
            current.set(curr);
            target.run();
        } finally {
            current.set(prev);
        }
    }

    public <P, R, T extends Throwable> R apply(ThrowingFunction<P, R, T> target, P p) throws T {
        ThreadContext curr = ThreadContext.this;
        ThreadContext prev = current.get();
        try {
            current.set(curr);
            return target.apply(p);
        } finally {
            current.set(prev);
        }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }
}
