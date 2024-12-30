/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core;

import one.chartsy.base.function.ThrowingSupplier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ThrowingSupplierTest {

    @Test
    public void get_can_throw_checked_Exception() {
        ThrowingSupplier<?,Exception> supplier = () -> { throw new Exception("expected"); };
        assertThrows(Exception.class, supplier::get);
    }

    @Test
    public void unchecked_gives_Supplier_lazily_evaluatable() {
        var evalNumber = new AtomicInteger();
        var lazyCode = ThrowingSupplier.unchecked(evalNumber::incrementAndGet);
        assertEquals(0, evalNumber.get());

        int result = lazyCode.get();
        assertEquals(1, result);
        assertEquals(1, evalNumber.get());
    }

    @Test
    public void unchecked_rethrows_original_RuntimeException() {
        assertThrows(RuntimeException.class,
                () -> ThrowingSupplier.unchecked(() -> { throw new RuntimeException("expected"); }).get());
    }

    @Test
    public void unchecked_rethrows_original_Error() {
        assertThrows(Error.class,
                () -> ThrowingSupplier.unchecked(() -> { throw new Error("expected"); }).get());
    }

    @Test
    public void unchecked_throws_UncheckedException_instead_of_checked() {
        assertThrows(ThrowingSupplier.UncheckedException.class,
                () -> ThrowingSupplier.unchecked(() -> { throw new Exception("expected"); }).get());
    }

    @Test
    public void unchecked_throws_UncheckedIOException_instead_of_IOException() {
        assertThrows(UncheckedIOException.class,
                () -> ThrowingSupplier.unchecked(() -> { throw new IOException("expected"); }).get());
    }
}
