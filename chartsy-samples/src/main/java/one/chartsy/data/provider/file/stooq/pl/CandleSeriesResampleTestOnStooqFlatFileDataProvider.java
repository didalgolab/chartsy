package one.chartsy.data.provider.file.stooq.pl;

import one.chartsy.*;
import one.chartsy.data.*;
import one.chartsy.data.batch.Batches;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.util.Pair;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CandleSeriesResampleTestOnStooqFlatFileDataProvider {

    public static void main(String[] args) throws IOException {
        FlatFileDataProvider dataProvider = FlatFileFormat.STOOQ
                .newDataProvider(Path.of("C:/Downloads/d_pl_txt(6).zip"));

        //CandleSeries series = dataProvider.queryForCandles(query).collect(Batches.toCandleSeries());
        Map<Pair<Double, String>, String> counts = new TreeMap<>();
        List<? extends SymbolIdentity> stocks = dataProvider.listSymbols(new SymbolGroup("/data/daily/pl/wse stocks"));
        System.out.println("Stocks: " + stocks.size());
        for (SymbolIdentity stock : stocks) {
            DataQuery<Candle> query = DataQuery.resource(SymbolResource.of(stock, TimeFrame.Period.DAILY))
                    .limit(500)
                    .build();
            CandleSeries series = dataProvider.queryForCandles(query).collect(Batches.toCandleSeries());

            if (series.length() == 0) {
                System.out.println("Empty series: " + stock);
                continue;
            }

            long cnt = 0, n = 10_000;
            for (int i = 0; i < n; i++) {
                Series<Candle> newSeries = series.resample(AdjustmentMethod.RELATIVE);

                double newClose = newSeries.getLast().close();
                if (newClose > series.getLast().close())
                    cnt++;
            }
            counts.put(Pair.of((cnt*10_000L/n)/100.0, stock.name()), stock.name());

            System.out.println("" + stock.name() + ": " + (cnt*10_000L/n)/100.0 + " %");
        }
        counts.forEach((k,v) -> System.out.println("# " + k + ": " + v));
    }
}
