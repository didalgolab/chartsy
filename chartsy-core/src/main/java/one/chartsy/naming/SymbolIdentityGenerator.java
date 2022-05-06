package one.chartsy.naming;

import one.chartsy.IdentifierType;
import one.chartsy.InstrumentType;
import one.chartsy.SymbolIdentity;
import org.openide.util.lookup.ServiceProvider;

import java.util.Map;
import java.util.Optional;

@ServiceProvider(service = SymbolIdentityGenerator.class)
public class SymbolIdentityGenerator {

    public SymbolIdentity generate(String name, InstrumentType type, IdentifierType identifierType) {
        return new SymbolIdentifier(name, Optional.ofNullable(type), identifierType);
    }

    public SymbolIdentity generate(String name, InstrumentType type, IdentifierType identifierType, Map<String,?> meta) {
        return generate(name, type, identifierType);
    }
}
