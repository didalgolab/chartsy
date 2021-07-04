package one.chartsy.data.function;

@FunctionalInterface
public interface IndexedToDoubleFunction<T> {

    /**
     * Applies this function to the given arguments.
     *
     * @param value
     *            the function object argument
     * @param index
     *            the function index argument
     * @return the function result
     */
    double applyAsDouble(T value, int index);
}
