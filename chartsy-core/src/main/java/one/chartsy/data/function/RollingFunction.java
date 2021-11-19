package one.chartsy.data.function;

public interface RollingFunction<T> {

    /**
     * Calculates the next value of the function for the specified index.
     *
     * @param index
     *            the index of the value to calculate
     * @return the function result
     */
    T calculateNext(int index);
}
