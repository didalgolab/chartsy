package one.chartsy;

/**
 * A container object that holds an incomplete value or result.
 *
 * @param <T> the type of contained value
 */
@FunctionalInterface
public interface Incomplete<T> {

    T get();

    default boolean isPresent() {
        return get() != null;
    }


    static <T> Incomplete<T> empty() {
        final class Empty implements Incomplete<Object> {
            private static final Empty INSTANCE = new Empty();

            @Override
            public Object get() {
                return null;
            }
        }
        @SuppressWarnings("unchecked")
        var empty = (Incomplete<T>) Empty.INSTANCE;
        return empty;
    }
}
