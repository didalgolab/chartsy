package one.chartsy.data.provider;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DataQuery;
import one.chartsy.data.batch.Batches;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.collections4.IterableUtils.transformedIterable;

public class DataProviders {
    private DataProviders() { }

    protected static Support getSupport() {
        return Support.INSTANCE;
    }

    public static List<CandleSeries> getHistoricalCandles(DataProvider provider, TimeFrame timeFrame, Iterable<? extends SymbolIdentity> symbols) {
        return getHistoricalCandles(provider, transformedIterable(symbols, symb -> SymbolResource.of(symb, timeFrame)));
    }

    public static List<CandleSeries> getHistoricalCandles(DataProvider provider, Iterable<SymbolResource<Candle>> resources) {
        return getSupport().getHistoricalCandles(provider, resources);
    }

    public static CandleSeries getHistoricalCandles(DataProvider provider, SymbolResource<Candle> resource) {
        return getSupport().getHistoricalCandles(provider, resource);
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
            return provider.queryForBatches(Candle.class, query).collect(Batches.toCandleSeries());
        }
    }
}
