package one.chartsy.text;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class Base62Test {

    Base62.Encoder encoder = Base62.getEncoder();
    Base62.Decoder decoder = Base62.getDecoder();

    @Test
    void test() {
        byte[] bytes = new byte[4];
        ThreadLocalRandom.current().nextBytes(bytes);
        System.out.println(encoder.encode(bytes));
    }

    @Test
    void encode_gives_only_alphanumeric_characters() {
        Pattern alphanumeric = Pattern.compile("[0-9A-Za-z]*");

        randomByteSequences()
                .limit(1000)
                .map(encoder::encode)
                .forEach(base62String ->
                        assertTrue(alphanumeric.matcher(base62String).matches(),
                                "Should be alphanumeric only but was: " + base62String));
    }

    @Test
    void decode_reverses_encoded_bytes() {
        randomByteSequences()
                .limit(1000)
                .forEach(bytes ->
                    assertArrayEquals(bytes, decoder.decode(encoder.encode(bytes))));
    }

    static Stream<byte[]> randomByteSequences() {
        final int MAX_BYTES_PER_SEQUENCE = 1000;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        return Stream.generate(() -> {
            byte[] randomBytes = new byte[ random.nextInt(MAX_BYTES_PER_SEQUENCE) ];
            random.nextBytes(randomBytes);
            return randomBytes;
        });
    }
}