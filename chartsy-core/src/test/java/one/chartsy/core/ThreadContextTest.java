/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ThreadContextTest {

    @Test void empty_gives_empty_ThreadContext() {
        var emptyContext = ThreadContext.empty();
        assertEquals(0, emptyContext.getVars().size());
    }

    @Test void of_Map_gives_ThreadContext_with_same_map_instance() {
        Map<?,?> expectedVars = Collections.singletonMap("K", "V");
        ThreadContext currCtx = ThreadContext.of(expectedVars);

        assertSame(expectedVars, currCtx.getVars());
    }

    @Test void current_throws_NotFoundException_when_not_in_contextual_call() {
        assertThrows(ThreadContext.NotFoundException.class, ThreadContext::current);
    }

    @Test void current_gives_current_ThreadContext_with_expectedVars_when_in_contextual_call() {
        var expectedVars = Map.of("K", "OK");
        ThreadContext.of(expectedVars).contextualRunnable(
                () -> assertEquals(expectedVars, ThreadContext.current().getVars())
        ).run();
    }

    @Test void contextualRunnable_gives_Runnable_evaluated_lazily() {
        var evalNumber = new AtomicInteger();
        var contextualRunnable = ThreadContext.empty().contextualRunnable(evalNumber::incrementAndGet);
        assertEquals(0, evalNumber.get());

        contextualRunnable.run();
        assertEquals(1, evalNumber.get());
    }

    @Test void contextualRunnable_gives_Runnable_evaluated_in_context() {
        var context = ThreadContext.of(Map.of());
        context.contextualRunnable(
                () -> assertEquals(context, ThreadContext.current())
        ).run();
    }

    @Test void contextualCallable_gives_Callable_evaluated_lazily() throws Exception {
        var evalNumber = new AtomicInteger();
        var contextualCallable = ThreadContext.empty().contextualCallable(evalNumber::incrementAndGet);
        assertEquals(0, evalNumber.get());

        assertEquals(1, contextualCallable.call());
        assertEquals(1, evalNumber.get());
    }

    @Test void contextualCallable_gives_Callable_evaluated_in_context() throws Exception {
        var context = ThreadContext.of(Map.of());
        var callContext = context.contextualCallable(ThreadContext::current).call();
        assertEquals(context, callContext);
    }

    @Test void contextual_call_rethrows_underlying_Exceptions_voiding_current_context() {
        var context = ThreadContext.empty();
        assertThrows(RaisedInContextException.class,
                () -> context.contextualRunnable(this::raiseInContextException).run(), "rethrows Exception from Runnable");
        assertThrows(RaisedInContextException.class,
                () -> context.contextualCallable(this::raiseInContextException).call(), "rethrows Exception from Callable");
        assertThrows(ThreadContext.NotFoundException.class, ThreadContext::current, "should void current context after contextual call");
    }

    private Void raiseInContextException() {
        throw new RaisedInContextException();
    }
}

class RaisedInContextException extends RuntimeException { }