package one.chartsy.data.function;

public interface RollingToDoubleFunction {

    /**
     * Calculates the next value of the function for the specified index.
     *
     * @param index
     *            the index of the value to calculate
     * @return the function result
     */
    double calculateNext(int index);
}
