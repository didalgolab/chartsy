package one.chartsy.naming;

import one.chartsy.SymbolIdentity;
import org.openide.util.lookup.ServiceProvider;

import java.util.Comparator;

@ServiceProvider(service = SymbolIdentityComparator.class)
public class SymbolIdentityComparator implements Comparator<SymbolIdentity> {

    @Override
    public int compare(SymbolIdentity o1, SymbolIdentity o2) {
        int cmp = o1.name().compareTo(o2.name());
        if (cmp == 0)
            cmp = o1.type().name().compareTo(o2.type().name());
        return cmp;
    }

    static class LazyHolder {
        static final SymbolIdentityComparator INSTANCE = new SymbolIdentityComparator();
    }
}
