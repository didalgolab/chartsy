/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider;

import one.chartsy.FinancialService;
import one.chartsy.SymbolGroup;
import one.chartsy.SymbolIdentity;
import one.chartsy.data.DataQuery;
import one.chartsy.time.Chronological;
import org.openide.util.Lookup;
import reactor.core.publisher.Flux;

import java.util.List;

public interface DataProvider extends FinancialService {
    @Override
    default Lookup getLookup() {
        return Lookup.EMPTY;
    }

    default List<SymbolGroup> listSymbolGroups() {
        return List.of(SymbolGroup.BASE);
    }

    default List<? extends SymbolIdentity> listSymbols() {
        return listSymbols(SymbolGroup.BASE);
    }

    List<? extends SymbolIdentity> listSymbols(SymbolGroup group);

    <T extends Chronological> Flux<T> query(Class<T> type, DataQuery<T> request);

    // TODO - to be continued...
}
