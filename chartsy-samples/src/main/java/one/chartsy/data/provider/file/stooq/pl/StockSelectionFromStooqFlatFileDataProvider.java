package one.chartsy.data.provider.file.stooq.pl;

import one.chartsy.*;
import one.chartsy.core.collections.DoubleMinMaxList;
import one.chartsy.data.*;
import one.chartsy.data.batch.Batches;
import one.chartsy.data.packed.PackedCandleSeries;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.finance.FinancialIndicators;
import one.chartsy.util.Pair;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

public class StockSelectionFromStooqFlatFileDataProvider {

    public static void main(String[] args) throws IOException {
        FlatFileDataProvider dataProvider = FlatFileFormat.STOOQ
                .newDataProvider(Path.of("C:/Users/Mariusz/Downloads/d_pl_txt(6).zip"));

        //CandleSeries series = dataProvider.queryForCandles(query).collect(Batches.toCandleSeries());
        Map<Pair<Double, String>, String> counts = new TreeMap<>();
        List<? extends SymbolIdentity> stocks = dataProvider.listSymbols(new SymbolGroup("/data/daily/pl/wse stocks"));
        System.out.println("Stocks: " + stocks.size());
        for (SymbolIdentity stock : stocks) {
            DataQuery<Candle> query = DataQuery.resource(SymbolResource.of(stock, TimeFrame.Period.DAILY))
                    //.limit(250)
                    //.endTime(LocalDateTime.of(2021, 10, 1, 0, 0))
                    .build();
            CandleSeries series = dataProvider.queryForCandles(query).collect(Batches.toCandleSeries());

            if (series.length() == 0) {
                System.out.println("Empty series: " + stock);
                continue;
            }
            DoubleMinMaxList bands = FinancialIndicators.Sfora.bands(PackedCandleSeries.from(series));
            DoubleSeries width = bands.getMaximum().sub(bands.getMinimum());
            DoubleSeries highestSince = PackedCandleSeries.from(series).highestSince();
            if (width.length() == 0)
                continue;

            double lastClose = series.getLast().close();
            double widthLast = width.getLast();
            double widthPercent = width.getLast() / lastClose;
            System.out.println("STOCK: " + stock.name() + " - " + series.getLast() + ": HighestSince=" + widthLast);
            int n = 1_000, cnt = 0;
            for (int i = 0; i < n; i++) {
                Series<Candle> newSeries = series.resample(AdjustmentMethod.RELATIVE);

                DoubleMinMaxList newBands = FinancialIndicators.Sfora.bands(PackedCandleSeries.from(newSeries));
                DoubleSeries newWidth = newBands.getMaximum().sub(newBands.getMinimum());
                DoubleSeries newHighestSince = PackedCandleSeries.from(newSeries).highestSince();
                double newLastClose = newSeries.getLast().close();
                double newWidthLast = newWidth.getLast();
                double newWidthPercent = newWidth.getLast() / newLastClose;
                if (/*newWidthPercent < widthPercent ||*/ newHighestSince.getLast() >= highestSince.getLast())
                    cnt++;
            }
            counts.put(Pair.of((cnt*10_000L/n)/100.0, stock.name()), stock.name());

            System.out.println("" + stock.name() + ": " + (cnt*10_000L/n)/100.0 + " %");
        }
        counts.forEach((k,v) -> System.out.println("# " + k + ": " + v));
    }
}
