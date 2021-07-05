package one.chartsy.naming;

import one.chartsy.AssetType;
import one.chartsy.SymbolIdentity;
import org.openide.util.lookup.ServiceProvider;

import java.util.Map;

@ServiceProvider(service = SymbolIdentityGenerator.class)
public class SymbolIdentityGenerator {

    public SymbolIdentity generate(String name, AssetType type) {
        return new SymbolIdentifier(name, type);
    }

    public SymbolIdentity generate(String name, AssetType type, Map<String,?> meta) {
        return generate(name, type);
    }
}
