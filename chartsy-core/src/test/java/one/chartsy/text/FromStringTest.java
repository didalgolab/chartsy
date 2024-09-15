package one.chartsy.text;

import org.junit.jupiter.api.Test;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class FromStringTest {

    private static final int TEST_ITERATIONS = 10_000; // tested with 1_000_000_000 too

    @Test
    void toDouble_shouldBeConsistentWithDoubleParseDouble() {
        var random = ThreadLocalRandom.current();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            var randomNumberString = generateRandomNumberString(random);

            double expectedResult = Double.parseDouble(randomNumberString.toString());
            double actualResult = FromString.toDouble(randomNumberString);

            assertEquals(expectedResult, actualResult,
                    "Mismatch for input: " + randomNumberString);
        }
    }

    private CharSequence generateRandomNumberString(RandomGenerator random) {
        var sb = new StringBuilder();
        var dotUsed = false;

        while (true) {
            int choice = random.nextInt(12); // 0-9 digits, dot, or end

            if (choice < 10) {
                sb.append(choice); // Append digit
            } else if (choice == 10 && !dotUsed) {
                sb.append('.'); // Append dot
                dotUsed = true;
            } else {
                break; // End of string
            }
        }

        // Ensure the string is not empty and isn't just a dot
        if (sb.isEmpty() || sb.length() == 1 && sb.charAt(0) == '.') {
            sb.append(random.nextInt(10));
        }

        return sb;
    }
}