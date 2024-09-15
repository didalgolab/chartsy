/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.financial;

import one.chartsy.SymbolIdentity;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = SymbolIdentityGenerator.class)
public class SymbolIdentityGenerator {

    public SymbolIdentity generate(String name, IdentityType type) {
        return new SymbolIdentifier(name, type);
    }
}
