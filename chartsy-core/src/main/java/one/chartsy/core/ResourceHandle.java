package one.chartsy.core;

public interface ResourceHandle<V> {

    V get();

    static <V> ResourceHandle<V> of(V v) {
        return new Of<>(v);
    }

    record Of<V>(V get) implements ResourceHandle<V> { }
}
