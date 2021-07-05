package one.chartsy.data;

import one.chartsy.SymbolResource;

public interface SymbolResourceData<E,D> {

    SymbolResource<E> getResource();

    D getData();
}
