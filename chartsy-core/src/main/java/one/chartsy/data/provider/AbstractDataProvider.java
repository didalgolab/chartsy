package one.chartsy.data.provider;

import one.chartsy.SymbolGroup;
import one.chartsy.SymbolIdentity;
import org.openide.util.Lookup;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

public abstract class AbstractDataProvider implements DataProvider {

    protected final InstanceContent lookupContent;
    private final Lookup lookup;


    protected AbstractDataProvider() {
        this(new InstanceContent());
    }

    protected AbstractDataProvider(InstanceContent lookupContent) {
        this.lookupContent = lookupContent;
        this.lookup = new AbstractLookup(lookupContent);
    }

    @Override
    public Lookup getLookup() {
        return lookup;
    }

    @Override
    public List<? extends SymbolIdentity> listSymbols(SymbolGroup group) {
        throw new UnsupportedOperationException("listSymbols by SymbolGroup");
    }

    public List<? extends SymbolIdentity> listSymbols(String... groups) {
        if (groups.length == 0)
            return List.of();
        if (groups.length == 1)
            return listSymbols(new SymbolGroup(groups[0]));

        return listSymbols(Stream.of(groups).map(SymbolGroup::new).toList());
    }

    public List<? extends SymbolIdentity> listSymbols(Collection<SymbolGroup> groups) {
        Set<SymbolIdentity> symbols = new TreeSet<>(SymbolIdentity.comparator());
        for (SymbolGroup group : groups)
            symbols.addAll(listSymbols(group));

        return List.copyOf(symbols);
    }
}