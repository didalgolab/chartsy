package one.chartsy;

import java.util.Optional;

public interface SymbolGroupContent {

    String getName();

    String getTypeName();

    Optional<SymbolIdentity> getSymbol();
}
