/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider.file.stooq.pl;

import one.chartsy.*;
import one.chartsy.data.*;
import one.chartsy.data.packed.PackedCandleSeries;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.financial.ValueIndicatorSupport;
import one.chartsy.financial.indicators.FramaTrendWhispers;
import one.chartsy.util.Pair;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class StockSelectionFromHourlyStooqFlatFileDataProvider {

    public static void main(String[] args) throws IOException {
        // create FlatFileDataProvider for a Stooq historical data file
        FlatFileDataProvider dataProvider = FlatFileFormat.STOOQ
                .newDataProvider(Path.of("C:/Downloads/h_pl_txt(1).zip"));

        // list all stocks contained in a file
        Map<Pair<Double, String>, String> counts = new TreeMap<>();
        List<? extends SymbolIdentity> stocks = dataProvider.listSymbols("/data/hourly/pl/wse stocks");
        System.out.println("Stocks: " + stocks.size());
        for (SymbolIdentity stock : stocks) {
            var resource = SymbolResource.of(stock, TimeFrame.Period.H1);
            var series = dataProvider.queryForCandles(
                            DataQuery.of(resource))
                    .collectSortedList()
                    .as(CandleSeries.of(resource));

            if (series.length() == 0) {
                System.out.println("Empty series: " + stock);
                continue;
            }
            DoubleSeries width = ValueIndicatorSupport.calculate(series, new FramaTrendWhispers(), FramaTrendWhispers::getRange);

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

                DoubleSeries newWidth = ValueIndicatorSupport.calculate(newSeries, new FramaTrendWhispers(), FramaTrendWhispers::getRange);
                DoubleSeries newHighestSince = PackedCandleSeries.from(newSeries).highestSince();
                double newLastClose = newSeries.getLast().close();
                double newWidthLast = newWidth.getLast();
                double newWidthPercent = newWidth.getLast() / newLastClose;
                if (newWidthPercent < widthPercent || newHighestSince.getLast() >= highestSince.getLast())
                    cnt++;
            }
            counts.put(Pair.of((cnt*10_000L/n)/100.0, stock.name()), stock.name());

            System.out.println("" + stock.name() + ": " + (cnt*10_000L/n)/100.0 + " %");
        }
        counts.forEach((k,v) -> System.out.println("# " + k + ": " + v));
    }
}
