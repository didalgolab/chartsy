package one.chartsy;

import one.chartsy.naming.SymbolIdentityComparator;
import one.chartsy.naming.SymbolIdentityGenerator;
import org.openide.util.Lookup;

import java.util.Comparator;
import java.util.Map;

/**
 * Represents a Symbol identified by {@code name} and {@code type}.
 */
public interface SymbolIdentity {

    String getName();
    AssetType getType();

    static SymbolIdentity of(String name) {
        return of(name, AssetTypes.GENERIC);
    }

    static SymbolIdentity of(String name, AssetType type) {
        return Lookup.getDefault().lookup(SymbolIdentityGenerator.class).generate(name, type);
    }

    static SymbolIdentity of(String name, AssetType type, Map<String,?> meta) {
        return Lookup.getDefault().lookup(SymbolIdentityGenerator.class).generate(name, type, meta);
    }

    static Comparator<SymbolIdentity> comparator() {
        return Lookup.getDefault().lookup(SymbolIdentityComparator.class);
    }
}
