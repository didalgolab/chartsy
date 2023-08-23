/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.naming;

import one.chartsy.IdentifierType;
import one.chartsy.AssetClass;
import one.chartsy.SymbolIdentity;
import org.openide.util.lookup.ServiceProvider;

import java.util.Map;
import java.util.Optional;

@ServiceProvider(service = SymbolIdentityGenerator.class)
public class SymbolIdentityGenerator {

    public SymbolIdentity generate(String name, AssetClass type, IdentifierType identifierType) {
        return new SymbolIdentifier(name, Optional.ofNullable(type), identifierType);
    }

    public SymbolIdentity generate(String name, AssetClass type, IdentifierType identifierType, Map<String,?> meta) {
        return generate(name, type, identifierType);
    }
}
