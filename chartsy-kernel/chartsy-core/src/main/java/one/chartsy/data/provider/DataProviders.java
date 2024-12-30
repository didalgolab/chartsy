/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DataQuery;
import one.chartsy.data.Series;
import one.chartsy.time.Chronological;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

public class DataProviders {
    private DataProviders() { }

    protected static Support getSupport() {
        return Support.INSTANCE;
    }

    public static List<CandleSeries> getHistoricalCandles(DataProvider provider, TimeFrame timeFrame, Iterable<? extends SymbolIdentity> symbols) {
        return getHistoricalCandles(provider, StreamSupport.stream(symbols.spliterator(), false)
                .map(symb -> SymbolResource.of(symb, timeFrame))
                .toList());
    }
    public static List<CandleSeries> getHistoricalCandles(DataProvider provider, Iterable<SymbolResource<Candle>> resources) {
        return getSupport().getHistoricalCandles(provider, resources);
    }

    public static CandleSeries getHistoricalCandles(DataProvider provider, SymbolResource<Candle> resource) {
        return getSupport().getHistoricalCandles(provider, resource);
    }

    public static <E extends Chronological> Series<E> getSeries(DataProvider provider, Class<E> type, SymbolResource<E> resource) {
        return getSupport().getSeries(provider, type, resource);
    }

    @ServiceProvider(service = Support.class)
    public static class Support {
        private static final Support INSTANCE = Lookup.getDefault().lookup(Support.class);
        public Support() { }

        protected List<CandleSeries> getHistoricalCandles(DataProvider provider, Iterable<SymbolResource<Candle>> symbols) {
            List<CandleSeries> resultList = new ArrayList<>();
            for (SymbolResource<Candle> symb : symbols)
                resultList.add(getHistoricalCandles(provider, symb));

            return resultList;
        }

        protected CandleSeries getHistoricalCandles(DataProvider provider, SymbolResource<Candle> resource) {
            DataQuery<Candle> query = DataQuery.of(resource);
            return provider.query(Candle.class, query)
                    .collectSortedList()
                    .as(CandleSeries.of(resource));
        }

        protected <E extends Chronological> Series<E> getSeries(DataProvider provider, Class<E> type, SymbolResource<E> resource) {
            DataQuery<E> query = DataQuery.of(resource);
            return provider.query(type, query)
                    .collectSortedList()
                    .as(Series.of(resource));
        }
    }
}
