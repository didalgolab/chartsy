package one.chartsy;

import one.chartsy.data.provider.DataProviderLoader;

import java.util.List;
import java.util.Optional;

public interface SymbolGroupContent {

    Long getId();

    String getName();

    String getTypeName();

    Optional<SymbolIdentity> getSymbol();

    List<SymbolGroupContent> getContent(SymbolGroupContentRepository repo, DataProviderLoader loader);
}
