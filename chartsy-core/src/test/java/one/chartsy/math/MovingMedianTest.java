package one.chartsy.math;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MovingMedianTest {

    private static final int DATA_SIZE = 1000;

    /** Generate window sizes for testing */
    private static Stream<Integer> windowSizes() {
        return Stream.of(1, 2, 3, 5, 10, 50, 100, 500, 998, 999, 1000, 1001, 1002, 2000);
    }

    private <T extends Comparable<T>> void testMedian(AbstractMovingMedian medianCalculator, Consumer<T> acceptFunction,
                                                      Supplier<T> getMedianFunction, List<T> dataStream,
                                                      BiFunction<T, T, T> meanFunction) {
        var window = new LinkedList<T>();

        for (T data : dataStream) {
            if (window.size() == medianCalculator.size) {
                window.poll();
            }
            window.offer(data);
            acceptFunction.accept(data);

            // Sort the window to calculate the expected median
            var sortedWindow = new ArrayList<>(window);
            sortedWindow.sort(Comparator.naturalOrder());
            T expectedMedian;
            int size = sortedWindow.size();
            if (size % 2 == 1) {
                expectedMedian = sortedWindow.get(size / 2);
            } else {
                T a = sortedWindow.get(size / 2 - 1);
                T b = sortedWindow.get(size / 2);
                expectedMedian = meanFunction.apply(a, b);
            }

            T calculatedMedian = getMedianFunction.get();
            assertEquals(expectedMedian, calculatedMedian, "Mismatch in median calculation");
        }
    }

    @ParameterizedTest
    @MethodSource("windowSizes")
    void doubleMedian(int windowSize) {
        var doubleDataStream = ThreadLocalRandom.current().doubles(DATA_SIZE, -100, 1000)
                .boxed()
                .toList();

        var medianCalculator = new MovingMedian.OfDouble(windowSize);

        testMedian(
                medianCalculator,
                medianCalculator::accept,
                medianCalculator::getMedian,
                doubleDataStream,
                (a, b) -> (a + b) / 2.0
        );
    }

    @ParameterizedTest
    @MethodSource("windowSizes")
    void genericMedian(int windowSize) {
        var intDataStream = ThreadLocalRandom.current().ints(DATA_SIZE, -100, 1000)
                .boxed()
                .toList();

        BiFunction<Integer, Integer, Integer> intMeanFunction = (a, b) -> (a + b) / 2;

        var medianCalculator = new MovingMedian<>(windowSize, intMeanFunction);

        testMedian(
                medianCalculator,
                medianCalculator,
                medianCalculator::getMedian,
                intDataStream,
                intMeanFunction
        );
    }

    @ParameterizedTest
    @MethodSource("windowSizes")
    void stringMedian(int windowSize) {
        List<String> stringDataStream = Stream.generate(() -> randomString(5))
                .limit(DATA_SIZE)
                .toList();

        BiFunction<String, String, String> stringMeanFunction = (a, b) -> a.compareTo(b) <= 0 ? a : b;

        var medianCalculator = new MovingMedian<>(windowSize, stringMeanFunction);

        testMedian(
                medianCalculator,
                medianCalculator,
                medianCalculator::getMedian,
                stringDataStream,
                stringMeanFunction
        );
    }

    @Test
    void windowSizeInvalid() {
        assertThrows(IllegalArgumentException.class, () -> new MovingMedian.OfDouble(0));
        assertThrows(IllegalArgumentException.class, () -> new MovingMedian.OfDouble(-1));
        assertThrows(IllegalArgumentException.class, () -> new MovingMedian<>(0));
        assertThrows(IllegalArgumentException.class, () -> new MovingMedian<>(-1));
    }

    @Test
    void windowSizeOne() {
        var medianCalculator = new MovingMedian.OfDouble(1);

        medianCalculator.accept(10.0);
        assertEquals(10.0, medianCalculator.getMedian());

        medianCalculator.accept(20.0);
        assertEquals(20.0, medianCalculator.getMedian());

        medianCalculator.accept(-5.0);
        assertEquals(-5.0, medianCalculator.getMedian());
    }

    @Test
    void emptyDataStream() {
        var medianCalculator = new MovingMedian.OfDouble(5);

        assertTrue(medianCalculator.isEmpty());
        assertThrows(NoSuchElementException.class, medianCalculator::getMedian,
                "Since there is no data, getMedian should throw NoSuchElementException");
    }

    @Test
    void identicalElements() {
        var medianCalculator = new MovingMedian.OfDouble(5);
        for (int i = 0; i < 10; i++) {
            medianCalculator.accept(42.0);
            assertEquals(42.0, medianCalculator.getMedian());
        }
    }

    @Test
    void negativeNumbers() {
        var medianCalculator = new MovingMedian.OfDouble(3);

        medianCalculator.accept(-10.0);
        assertEquals(-10.0, medianCalculator.getMedian());
        medianCalculator.accept(-20.0);
        assertEquals(-15.0, medianCalculator.getMedian());
        medianCalculator.accept(-5.0);
        assertEquals(-10.0, medianCalculator.getMedian());
        medianCalculator.accept(-0.0);
        assertEquals(-5.0, medianCalculator.getMedian());
    }

    @Test
    void nanAndInfinity() {
        var medianCalculator = new MovingMedian.OfDouble(5);
        medianCalculator.accept(Double.NaN);
        medianCalculator.accept(Double.POSITIVE_INFINITY);
        medianCalculator.accept(Double.NEGATIVE_INFINITY);
        medianCalculator.accept(0.0);
        medianCalculator.accept(1.0);

        var window = Arrays.asList(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, 1.0);
        var sortedWindow = window.stream()
                .filter(d -> !Double.isNaN(d))
                .sorted()
                .toList();
        double expectedMedian = sortedWindow.get(sortedWindow.size() / 2);

        double calculatedMedian = medianCalculator.getMedian();
        assertEquals(expectedMedian, calculatedMedian, "Mismatch in median calculation with NaN and Infinity");
    }

    @Test
    void largeWindowSize() {
        final int windowSize = 100_000;
        final int dataSize = 200_000;
        var medianCalculator = new MovingMedian.OfDouble(windowSize);

        ThreadLocalRandom.current().doubles(dataSize, -100, 1000)
                .forEach(medianCalculator);

        double calculatedMedian = medianCalculator.getMedian();
        assertTrue(Double.isFinite(calculatedMedian));
    }

    /** Utility method to generate a random string of given length. */
    private static String randomString(int length) {
        return ThreadLocalRandom.current().ints(length, 'a', 'z' + 1)
                .mapToObj(c -> String.valueOf((char) c))
                .collect(Collectors.joining());
    }
}
