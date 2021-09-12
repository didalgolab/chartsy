/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.util;

import java.beans.ConstructorProperties;
import java.io.Serializable;
import java.util.Objects;

/**
 * An immutable pair consisting of two elements.
 * <p>
 * This class refers to the elements as 'left' and 'right'. It's implementation
 * is immutable. However, since there is no restriction on the type of the
 * objects that may be stored, if mutable objects are stored in the pair, then
 * the pair itself effectively becomes mutable.
 *
 * @param <L>
 *            the left element type
 * @param <R>
 *            the right element type
 *
 * @author Mariusz Bernacki
 *
 */
public final class Pair<L, R> implements Comparable<Pair<L, R>>, Serializable {

    /** Serialization version */
    private static final long serialVersionUID = 4954918890077093841L;

    /** The left object. */
    private final L left;

    /** The right object. */
    private final R right;


    /**
     * Obtains a pair from two objects inferring the generic types.
     * <p>
     * This factory allows the pair to be created using inference to obtain the
     * generic types.
     *
     * @param <L>
     *            the left element type
     * @param <R>
     *            the right element type
     * @param left
     *            the left element, may be {@code null}
     * @param right
     *            the right element, may be {@code null}
     * @return a new pair formed from the two parameters
     */
    public static <L, R> Pair<L, R> of(L left, R right) {
        return new Pair<>(left, right);
    }

    /**
     *
     *
     * @param left
     * @param right
     * @return
     */
    public static <T> T nvl(T left, T right) {
        return (left == null)? right : left;
    }

    /**
     * Creates a new {@code Pair} instance.
     *
     * @param left
     *            the left value, may be {@code null}
     * @param right
     *            the right value, may be {@code null}
     */
    @ConstructorProperties({"left", "right"})
    public Pair(L left, R right) {
        this.left = left;
        this.right = right;
    }

    /**
     * Gets the left element from this pair.
     * <p>
     * When treated as a key-value pair, this is the key.
     *
     * @return the left element, may be null
     */
    public L getLeft() {
        return left;
    }

    /**
     * Gets the right element from this pair.
     * <p>
     * When treated as a key-value pair, this is the value.
     *
     * @return the right element, may be null
     */
    public R getRight() {
        return right;
    }

    /**
     * Compares the pair based on the left element followed by the right element
     * (lexicographical order).
     * <p>
     * The types must be {@code Comparable}. If either {@code left} or {@code right}
     * is {@code null}, a {@code null} object is less than a non- {@code null}
     * object
     *
     * @param other
     *            the other pair, not null
     * @return negative if this is less, zero if equal, positive if greater
     */
    @Override
    public int compareTo(Pair<L, R> other) {
        int cmp = cmp(getLeft(), other.getLeft());
        if (cmp != 0)
            return cmp;

        return cmp(getRight(), other.getRight());
    }

    /**
     * Static helper methods for object comparisons, respecting {@code null}'s.
     */
    @SuppressWarnings("unchecked")
    private static int cmp(Object lhs, Object rhs) {
        if (lhs == null)
            return (rhs == null)? 0 : -1;
        if (rhs == null)
            return 1;

        return ((Comparable<Object>) lhs).compareTo(rhs);
    }

    /**
     * Compares this pair to another based on the two elements.
     *
     * @param obj
     *            the object to compare to, {@code null} returns {@code false}
     * @return {@code true} if the elements of the pair are equal
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;

        if (obj instanceof Pair<?, ?>) {
            Pair<?, ?> other = (Pair<?, ?>) obj;
            return Objects.equals(getLeft(), other.getLeft())
                    && Objects.equals(getRight(), other.getRight());
        }
        return false;
    }

    /**
     * Returns a hash code.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(getLeft()) + Objects.hashCode(getRight());
    }

    /**
     * Returns a String representation of this pair using the format
     * {@code ($left,$right)}.
     *
     * @return a string describing this object
     */
    @Override
    public String toString() {
        return "(" + getLeft() + ',' + getRight() + ')';
    }

    /**
     * Formats the receiver using the given format.
     * <p>
     * This uses {@link java.util.Formattable} to perform the formatting. Two
     * variables may be used to embed the left and right elements. Use
     * {@code %1$s} for the left element (key) and {@code %2$s} for the right
     * element (value). The default format used by {@code toString()} is
     * {@code (%1$s,%2$s)}.
     *
     * @param format
     *            the format string, optionally containing {@code %1$s} and
     *            {@code %2$s}
     * @return the formatted string
     */
    public String toString(String format) {
        return String.format(format, getLeft(), getRight());
    }
}