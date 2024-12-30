/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core;

import one.chartsy.base.function.ThrowingRunnable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ThrowingRunnableTest {

    @Test
    public void run_can_throw_checked_Exception() {
        ThrowingRunnable<Exception> code = () -> { throw new Exception("expected"); };
        assertThrows(Exception.class, code::run);
    }

    @Test
    public void unchecked_gives_Runnable_lazily_evaluatable() {
        var evalNumber = new AtomicInteger();
        var lazyAction = ThrowingRunnable.unchecked(evalNumber::incrementAndGet);
        assertEquals(0, evalNumber.get());

        lazyAction.run();
        assertEquals(1, evalNumber.get());
    }

    @Test
    public void unchecked_rethrows_original_RuntimeException() {
        assertThrows(RuntimeException.class,
                () -> ThrowingRunnable.unchecked(() -> { throw new RuntimeException("expected"); }).run());
    }

    @Test
    public void unchecked_rethrows_original_Error() {
        assertThrows(Error.class,
                () -> ThrowingRunnable.unchecked(() -> { throw new Error("expected"); }).run());
    }

    @Test
    public void unchecked_throws_UncheckedException_instead_of_checked() {
        assertThrows(ThrowingRunnable.UncheckedException.class,
                () -> ThrowingRunnable.unchecked(() -> { throw new Exception("expected"); }).run());
    }

    @Test
    public void unchecked_throws_UncheckedIOException_instead_of_IOException() {
        assertThrows(UncheckedIOException.class,
                () -> ThrowingRunnable.unchecked(() -> { throw new IOException("expected"); }).run());
    }
}
