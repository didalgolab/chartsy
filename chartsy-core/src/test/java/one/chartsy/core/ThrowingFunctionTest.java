package one.chartsy.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ThrowingFunctionTest {

    @Test
    public void apply_can_throw_checked_Exception() {
        ThrowingFunction<?,?,Exception> function = p -> { throw new Exception("expected"); };
        assertThrows(Exception.class, () -> function.apply(null));
    }

    @Test
    public void unchecked_gives_Function_lazily_evaluated() {
        var evalNumber = new AtomicInteger();
        var lazyFunction = ThrowingFunction.unchecked(evalNumber::addAndGet);
        assertEquals(0, evalNumber.get());

        int result = lazyFunction.apply(1);
        assertEquals(1, result);
        assertEquals(1, evalNumber.get());
    }

    @Test
    public void unchecked_rethrows_original_RuntimeException() {
        assertThrows(RuntimeException.class,
                () -> ThrowingFunction.unchecked(__ -> { throw new RuntimeException("expected"); }).apply(null));
    }

    @Test
    public void unchecked_rethrows_original_Error() {
        assertThrows(Error.class,
                () -> ThrowingFunction.unchecked(__ -> { throw new Error("expected"); }).apply(null));
    }

    @Test
    public void unchecked_throws_UncheckedException_instead_of_checked() {
        assertThrows(ThrowingFunction.UncheckedException.class,
                () -> ThrowingFunction.unchecked(__ -> { throw new Exception("expected"); }).apply(null));
    }

    @Test
    public void unchecked_throws_UncheckedIOException_instead_of_IOException() {
        assertThrows(UncheckedIOException.class,
                () -> ThrowingFunction.unchecked(__ -> { throw new IOException("expected"); }).apply(null));
    }
}
