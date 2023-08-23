/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade;

import one.chartsy.naming.SymbolIdentifier;

import java.util.Set;

public interface MarketUniverse {

    Set<SymbolIdentifier> activeSymbols();


    record Of(Set<SymbolIdentifier> activeSymbols) implements MarketUniverse {
        /*
         * The canonical constructor copies the 'activeSymbols' set into
         * an unmodifiable set.
         */

        // explicit canonical constructor
        public Of(Set<SymbolIdentifier> activeSymbols) {
            this.activeSymbols = Set.copyOf(activeSymbols);
        }
    }
}
