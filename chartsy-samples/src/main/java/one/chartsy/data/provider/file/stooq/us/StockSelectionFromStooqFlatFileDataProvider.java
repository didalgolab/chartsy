package one.chartsy.data.provider.file.stooq.us;

import one.chartsy.*;
import one.chartsy.core.collections.DoubleMinMaxList;
import one.chartsy.data.*;
import one.chartsy.data.packed.PackedCandleSeries;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.finance.FinancialIndicators;
import one.chartsy.util.Pair;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class StockSelectionFromStooqFlatFileDataProvider {

    public static void main(String[] args) throws IOException {
        // create FlatFileDataProvider for a Stooq historical data file
        FlatFileDataProvider dataProvider = FlatFileFormat.STOOQ
                .newDataProvider(Path.of("C:/Downloads/d_us_txt(1).zip"));

        // list all symbol groups containing stock symbols
        List<SymbolGroup> stockGroups = dataProvider.listSymbolGroups(
                group -> group.name().contains("stocks") /*&& !group.name().contains("nysemkt")*/);
        System.out.println("Stock groups: " + stockGroups);

        // list all stocks contained in a file
        Map<Pair<Double, String>, String> counts = new TreeMap<>();
        List<? extends SymbolIdentity> stocks = dataProvider.listSymbols(stockGroups);
        System.out.println("Stocks: " + stocks.size());
        for (SymbolIdentity stock : stocks) {
            var resource = SymbolResource.of(stock, TimeFrame.Period.DAILY);
            CandleSeries series = dataProvider.queryForCandles(
                            DataQuery.resource(resource).limit(250).build())
                    .collectSortedList()
                    .as(CandleSeries.of(resource));

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
