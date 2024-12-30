/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.text;

/**
 * A movable portion of a String, implementing the {@link CharSequence} interface.
 * <p>
 * This class allows efficient manipulation and access to a portion of a larger string
 * without creating new String objects.
 * <p>
 * <strong>Thread Safety:</strong> This class is <b>not thread-safe.</b> As it is mutable,
 * concurrent access from multiple threads can lead to race conditions and unexpected behavior.
 * It should not be used in multithreaded contexts without external synchronization.
 * <p>
 * <strong>Usage in Collections:</strong> This class implements {@link #equals(Object)} and
 * {@link #hashCode()}, allowing it to be used for querying {@link java.util.Map}s using methods
 * like {@link java.util.Map#get(Object)}, {@link java.util.Map#containsKey(Object)}, or similar.
 * However, due to its mutable nature, it cannot be directly used as a key in Maps or other
 * collections that rely on immutable keys. To use this object as a key, it should be first
 * converted to an immutable char sequence using its {@link #toString()} method.
 *
 * @author Mariusz Bernacki
 *
 */
public class MutableStringFragment implements CharSequence {

    protected final String line;
    protected int start;
    protected int end;

    /**
     * Constructs a new {@code MutableStringFragment} representing the portion of the given string
     * starting at the specified start index (inclusive) and ending at the specified end index (exclusive).
     *
     * @param line  the underlying string
     * @param start the starting index (inclusive)
     * @param end   the ending index (exclusive)
     */
    public MutableStringFragment(String line, int start, int end) {
        this.line = line;
        this.start = start;
        this.end = end;
    }

    /**
     * Constructs a new {@code MutableStringFragment} for the entire given string.
     *
     * @param line the underlying string
     */
    public MutableStringFragment(String line) {
        this(line, 0, line.length());
    }

    /**
     * Calculates the hash code for this character sequence.
     * The hash code is equivalent to the hash code of the string representation
     * of this sequence, as defined by {@link CharSequence#toString()}.
     *
     * <p>The implementation is based on the standard Java string hashing algorithm:
     *
     * <pre>
     *     int h = 0;
     *     for (int i = 0; i < length(); i++)
     *         h = 31*h + charAt(i);
     *     return h;
     * </pre>
     *
     * @return the hash code for this character sequence
     */
    @Override
    public int hashCode() {
        int h = 0;
        for (int i = 0; i < length(); i++) {
            h = 31 * h + charAt(i);
        }
        return h;
    }

    /**
     * Compares this character sequence to another object for equality.
     * The comparison is based on the content of the sequences, as defined by
     * {@link CharSequence#compare(CharSequence, CharSequence)}.
     *
     * @param obj the object to compare to
     * @return {@code true} if the objects are equal, {@code false} otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CharSequence other) {
            return CharSequence.compare(this, other) == 0;
        }
        return super.equals(obj);
    }

    @Override
    public int length() {
        return end - start;
    }

    @Override
    public char charAt(int index) {
        if (index < 0 || index >= length()) {
            throw new IndexOutOfBoundsException();
        }
        return line.charAt(start + index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        if (start < 0 || end > length() || start > end) {
            throw new IndexOutOfBoundsException();
        }
        return line.subSequence(this.start + start, this.start + end);
    }

    /**
     * Removes leading and trailing whitespace from the fragment without creating a new string.
     * This method modifies the current object by adjusting the start and end indices.
     * <p>
     * This is especially useful in data processing tasks where whitespace normalization is required.
     */
    public void trim() {
        while (start < end && Character.isWhitespace(line.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(line.charAt(end - 1))) {
            end--;
        }
    }

    @Override
    public String toString() {
        return line.substring(start, end);
    }
}