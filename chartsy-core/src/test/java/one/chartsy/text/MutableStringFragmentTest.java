/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.text;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

class MutableStringFragmentTest {

    @Test
    @DisplayName("Constructor creates a fragment with correct start and end indices")
    void testConstructor() {
        var helloFragment = new MutableStringFragment("Hello, World!", 0, 5);

        assertEquals(5, helloFragment.length());
        assertEquals("Hello", helloFragment.toString());
        assertEquals("Hello", helloFragment.subSequence(0, 5).toString());
    }

    @Test
    @DisplayName("charAt gives correct characters")
    void testCharAt() {
        var worldFragment = new MutableStringFragment("Hello, World!", 7, 12);

        assertEquals('W', worldFragment.charAt(0));
        assertEquals('d', worldFragment.charAt(4));

        assertEquals("World", IntStream.range(0, worldFragment.length())
                .mapToObj(worldFragment::charAt)
                .map(String::valueOf)
                .collect(Collectors.joining()));
    }

    @Test
    @DisplayName("charAt throws IndexOutOfBoundsException for invalid index")
    void testCharAtOutOfBounds() {
        var helloFragment = new MutableStringFragment("Hello", 0, 5);

        assertThrows(IndexOutOfBoundsException.class, () -> helloFragment.charAt(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> helloFragment.charAt(5));
    }

    @Test
    @DisplayName("subSequence gives correct subsequence")
    void testSubSequence() {
        var fragment = new MutableStringFragment("Hello, World!");

        assertEquals("World", fragment.subSequence(7, 12).toString());
    }

    @Test
    @DisplayName("subSequence throws IndexOutOfBoundsException for invalid indices")
    void testSubSequenceOutOfBounds() {
        var helloFragment = new MutableStringFragment("Hello", 0, 5);

        assertThrows(IndexOutOfBoundsException.class, () -> helloFragment.subSequence(-1, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> helloFragment.subSequence(2, 6));
        assertThrows(IndexOutOfBoundsException.class, () -> helloFragment.subSequence(3, 2));
    }

    @Test
    @DisplayName("toString gives the correct string representation")
    void testToString() {
        var worldFragment = new MutableStringFragment("Hello, World!", 7, 12);

        assertEquals("World", worldFragment.toString());
    }

    @Test
    @DisplayName("equals gives true for equal fragments")
    void testEquals() {
        var fragment1 = new MutableStringFragment("Hello, World!", 0, 5);
        var fragment2 = new MutableStringFragment("Hello, Java!", 0, 5);

        assertEquals(fragment1, fragment2);
    }

    @Test
    @DisplayName("equals gives false for different fragments")
    void testNotEquals() {
        var fragment1 = new MutableStringFragment("Hello, World!", 0, 5);
        var fragment2 = new MutableStringFragment("Hello, World!", 7, 12);

        assertNotEquals(fragment1, fragment2);
    }

    @ParameterizedTest
    @CsvSource({
            "'Hello, World!', 0, 5, 'Hello, World!', 0, 5, true",
            "'Hello, World!', 0, 5, 'Hello, Java!', 0, 5, true",
            "'Hello, World!', 0, 5, 'Hello, World!', 7, 12, false",
            "Hello, 0, 5, hello, 0, 5, false"
    })
    @SuppressWarnings("EqualsBetweenInconvertibleTypes")
    @DisplayName("equals correctly compares fragments between inconvertible types")
    void testEqualsParameterized(String s1, int start1, int end1, String s2, int start2, int end2, boolean expected) {
        MutableStringFragment stringFragment = new MutableStringFragment(s1, start1, end1);
        CharSequence          subSequence = s2.substring(start2, end2);

        assertEquals(expected, stringFragment.equals(subSequence));
    }

    @Test
    @DisplayName("hashCode gives the same value for equal fragments")
    void testHashCode() {
        var helloFragment1 = new MutableStringFragment("Hello, World!", 0, 5);
        var helloFragment2 = new MutableStringFragment("Hello, Java!", 0, 5);

        assertEquals(helloFragment1.hashCode(), helloFragment2.hashCode());
    }

    @ParameterizedTest
    @CsvSource({
            "''",  // Empty string
            "' '",  // Single space
            "'  \t  \n  '",  // Only whitespace characters
            "'a'",  // Single non-whitespace character
            "' a '",  // Single character with spaces
            "'a b c'"  // Multiple words with spaces
    })
    void testTrim(String input) {
        var fragment = new MutableStringFragment(input, 0, input.length());
        var expected = input.trim();

        fragment.trim(); // in place trimming of the Fragment
        String actual = fragment.toString();

        assertEquals(expected, actual, "Trim failed for input: '" + input + "'");
        assertEquals(expected, input.substring(fragment.start, fragment.end),
                "Fragment indices represent the incorrect portion of the original string for input: '" + input + "'");
    }

    @Test
    @SuppressWarnings("SuspiciousMethodCalls")
    @DisplayName("MutableStringFragment can be used to query a Map without conversion to String")
    void testMapQuery() {
        var map = new HashMap<String, Integer>();
        map.put("Hello", 1);
        map.put("World", 2);

        var helloFragment = new MutableStringFragment("Hello, World!", 0, 5);

        assertTrue(map.containsKey(helloFragment));
        assertEquals(1, map.get(helloFragment));
    }

    @Test
    @DisplayName("Demonstrate potential issues with using MutableStringFragment as Map key")
    void testMapKeyMutability() {
        var map = new HashMap<MutableStringFragment, Integer>();
        var key = new MutableStringFragment("Hello, World!", 0, 5);
        map.put(key, 1);

        assertTrue(map.containsKey(key));
        assertEquals(1, map.get(key));

        // Mutate the key
        key.start = 7;
        key.end = 12;

        // The mutated key can no longer be found in the map
        assertFalse(map.containsKey(key));
        assertNull(map.get(key));

        // The original entry is still in the map, but can't be retrieved with the mutated key
        assertEquals(1, map.size());
    }

    @ParameterizedTest
    @MethodSource("splitTestCases")
    void testForSplit(String input, char delimiter, List<String> expected) {
        var iterator = MutableStringFragmentIterator.forSplit(input, delimiter);
        var actual = new ArrayList<String>();

        while (iterator.hasNext()) {
            actual.add(iterator.next().toString());
        }

        assertEquals(expected, actual,
                String.format("Splitting '%s' with delimiter '%c' did not produce expected result", input, delimiter));

        assertFalse(iterator.hasNext(), "Iterator should be exhausted after consuming all elements");
        assertThrows(NoSuchElementException.class, iterator::next,
                "Calling next() on exhausted iterator should throw NoSuchElementException");
    }

    private static Stream<Arguments> splitTestCases() {
        return Stream.of(
                Arguments.of("a|b|c", '|', List.of("a", "b", "c")),
                Arguments.of("a||c", '|', List.of("a", "", "c")),
                Arguments.of("|b|", '|', List.of("", "b", "")),
                Arguments.of("||c||e||", '|', List.of("", "", "c", "", "e", "", "")),
                Arguments.of("abc", '|', List.of("abc")),
                Arguments.of("", '|', List.of("")),
                Arguments.of("|", '|', List.of("", "")),
                Arguments.of("||", '|', List.of("", "", "")),
                Arguments.of("a:b:c", ':', List.of("a", "b", "c")),
                Arguments.of("a b c", ' ', List.of("a", "b", "c")),
                Arguments.of("a\nb\nc", '\n', List.of("a", "b", "c"))
        );
    }
}