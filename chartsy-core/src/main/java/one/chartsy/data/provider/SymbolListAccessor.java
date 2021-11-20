package one.chartsy.data.provider;

import one.chartsy.SymbolGroup;
import one.chartsy.SymbolIdentity;

import java.util.List;

@FunctionalInterface
public interface SymbolListAccessor {

    List<? extends SymbolIdentity> listSymbols(SymbolGroup group);
}
