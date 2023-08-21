package one.chartsy.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LongFeistelCipherTest {

    @Test
    void encrypt_is_reversible() {
        LongFeistelCipher cipher = new LongFeistelCipher();
        final long range = 10_000_000_000L;
        for (long value = -range; value < range; value++)
            assertEquals(value, cipher.decode(cipher.encode(value)));
    }
}