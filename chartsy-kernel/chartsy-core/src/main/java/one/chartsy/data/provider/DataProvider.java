/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider;

import one.chartsy.FinancialService;
import one.chartsy.SymbolGroup;
import one.chartsy.SymbolIdentity;
import one.chartsy.TimeFrame;
import one.chartsy.data.DataQuery;
import one.chartsy.time.Chronological;
import org.openide.util.Lookup;
import reactor.core.publisher.Flux;

import java.util.List;

public interface DataProvider extends FinancialService {
    /**
     * A no-op {@link DataProvider} instance that returns no symbols and no data.
     * <p>
     * Intended as a safe default in places where a provider is required but none is configured.
     */
    DataProvider EMPTY = emptyProvider();

    @Override
    default Lookup getLookup() {
        return Lookup.EMPTY;
    }

    default List<SymbolGroup> listSymbolGroups() {
        return List.of(SymbolGroup.BASE);
    }

    default List<SymbolIdentity> listSymbols() {
        return listSymbols(SymbolGroup.BASE);
    }

    List<SymbolIdentity> listSymbols(SymbolGroup group);

    <T extends Chronological> Flux<T> query(Class<T> type, DataQuery<T> request);

    default List<TimeFrame> getAvailableTimeFrames(SymbolIdentity symbol) {
        return List.of(TimeFrame.Period.DAILY, TimeFrame.Period.WEEKLY,
                TimeFrame.Period.MONTHLY, TimeFrame.Period.QUARTERLY, TimeFrame.Period.YEARLY);
    }

    // TODO - to be continued...

    private static DataProvider emptyProvider() {
        class Empty implements DataProvider {
            @Override
            public String getName() {
                return "EMPTY";
            }

            @Override
            public List<SymbolGroup> listSymbolGroups() {
                return List.of();
            }

            @Override
            public List<SymbolIdentity> listSymbols(SymbolGroup group) {
                return List.of();
            }

            @Override
            public <T extends Chronological> Flux<T> query(Class<T> type, DataQuery<T> request) {
                return Flux.empty();
            }

            @Override
            public List<TimeFrame> getAvailableTimeFrames(SymbolIdentity symbol) {
                return List.of();
            }
        }

        return new Empty();
    }
}
