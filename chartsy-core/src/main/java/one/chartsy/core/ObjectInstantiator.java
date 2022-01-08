package one.chartsy.core;

@FunctionalInterface
public interface ObjectInstantiator<T> {

    T newInstance();
}
