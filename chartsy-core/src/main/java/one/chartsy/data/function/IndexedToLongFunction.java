package one.chartsy.data.function;

@FunctionalInterface
public interface IndexedToLongFunction<T> {

    /**
     * Applies this function to the given arguments.
     *
     * @param value
     *            the function object argument
     * @param index
     *            the function index argument
     * @return the function result
     */
    long applyAsLong(T value, int index);
}
