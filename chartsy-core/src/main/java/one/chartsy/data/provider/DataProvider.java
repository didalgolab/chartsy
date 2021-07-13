package one.chartsy.data.provider;

import one.chartsy.FinancialService;
import one.chartsy.SymbolGroup;
import one.chartsy.SymbolIdentity;
import org.openide.util.Lookup;

import java.util.Collection;
import java.util.List;

public interface DataProvider extends FinancialService {
    @Override
    default Lookup getLookup() {
        return Lookup.EMPTY;
    }

    default Collection<SymbolGroup> getAvailableGroups() {
        return List.of(SymbolGroup.BASE);
    }

    default List<SymbolIdentity> getSymbols() {
        return getSymbols(SymbolGroup.BASE);
    }

    List<SymbolIdentity> getSymbols(SymbolGroup group);

    // TODO - to be continued...
}
