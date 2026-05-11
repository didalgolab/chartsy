package one.chartsy.charting.util.collections;

import java.io.Serializable;
import java.util.Objects;

/// Holds two retained values for equality-based composite keys.
///
/// `DateFormatFactoryExt` currently nests this type when cached formatters are keyed by a
/// pattern, locale, and optional time zone. The API intentionally stops at construction,
/// equality, hashing, and a debugging string, which keeps the class suitable for short-lived
/// internal key objects without exposing a broader tuple abstraction from this package.
///
/// The container itself is immutable, but it retains both element references exactly as
/// supplied. Mutating a retained element after storing the pair in a hash-based collection can
/// therefore change the pair's logical identity in that collection.
public class Pair<E1, E2> implements Serializable {
    private final E1 first;
    private final E2 second;

    /// Creates a pair that retains the supplied references.
    ///
    /// @param first value stored in the first position
    /// @param second value stored in the second position
    public Pair(E1 first, E2 second) {
        this.first = first;
        this.second = second;
    }

    /// Returns whether `obj` is a pair with equal elements in both positions.
    ///
    /// Equality compares the retained references position by position using
    /// {@link Objects#equals(Object, Object)}.
    @Override
    public boolean equals(Object obj) {
        return obj instanceof Pair<?, ?> other
                && Objects.equals(first, other.first)
                && Objects.equals(second, other.second);
    }

    /// Returns a hash code derived from both stored elements.
    ///
    /// The computation matches [#equals(Object)] and preserves the legacy mixing formula used by
    /// earlier charting releases.
    @Override
    public int hashCode() {
        int firstHash = (first == null) ? 0 : first.hashCode();
        int secondHash = (second == null) ? 0 : second.hashCode();
        return firstHash + 0x58f * secondHash;
    }

    /// Returns a debugging form of this pair.
    ///
    /// The format is `[first:second]`.
    @Override
    public String toString() {
        return "[" + first + ':' + second + ']';
    }
}
