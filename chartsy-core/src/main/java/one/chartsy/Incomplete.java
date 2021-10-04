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
        @SuppressWarnings("unchecked")
        Incomplete<T> ic = (Incomplete<T>) EmptyIncomplete.instance;
        return ic;
    }
}

final class EmptyIncomplete implements Incomplete<Object> {
    static final EmptyIncomplete instance = new EmptyIncomplete();

    @Override
    public Object get() {
        return null;
    }
}
