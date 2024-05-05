/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider.file.stooq.us;

import one.chartsy.*;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DataQuery;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.util.Pair;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

public class VolatilityBasedSelectionFromStooqFlatFileDataProvider {

    private static final Path PATH_TO_STOOQ_FILE = Path.of("C:/Downloads/d_us_txt(1).zip");
    private static final int TOP_STOCKS_LIMIT = 20;
    private static final int INDEX = 4;
    private static final Logger log = LogManager.getLogger(VolatilityBasedSelectionFromStooqFlatFileDataProvider.class);

    public static void main(String[] args) throws IOException {
        // create FlatFileDataProvider for a Stooq-based historical data file
        FlatFileDataProvider dataProvider = FlatFileFormat.STOOQ
                .newDataProvider(PATH_TO_STOOQ_FILE);

        // list all symbol groups containing stock symbols
        List<SymbolGroup> stockGroups = dataProvider.listSymbolGroups(
                group -> group.name().contains("stocks") /*&& !group.name().contains("nysemkt")*/);
        System.out.println("Stock groups: " + stockGroups);

        // list all stocks contained in a file
        Map<Pair<Double, String>, String> counts = new TreeMap<>();
        PriorityQueue<Pair<SymbolIdentity, ExtremaParams>> topStocks = new PriorityQueue<>(
                Comparator.comparingDouble(a -> a.getRight().coefficient())
        );
        List<? extends SymbolIdentity> stocks = dataProvider.listSymbols(stockGroups);
        System.out.println("Stocks: " + stocks.size());
        for (SymbolIdentity stock : stocks) {
            var resource = SymbolResource.of(stock, TimeFrame.Period.DAILY);
            var candles = dataProvider.queryForCandles(
                            DataQuery.of(resource))
                    .collectSortedList()
                    .as(CandleSeries.of(resource));
            if (false) {
                System.out.println(candles.get(INDEX));
                break;
            }
            ExtremaParams params = detectDecreasingVolatility(candles);
            addStockToTop(stock, params, topStocks);
        }

        printTopStocks(topStocks);
    }

    private static void addStockToTop(SymbolIdentity stock, ExtremaParams params, PriorityQueue<Pair<SymbolIdentity, ExtremaParams>> topStocks) {
        topStocks.add(Pair.of(stock, params));
        if (topStocks.size() > TOP_STOCKS_LIMIT) {
            topStocks.poll();
        }
    }

    private static void printTopStocks(PriorityQueue<Pair<SymbolIdentity, ExtremaParams>> topStocks) {
        while (!topStocks.isEmpty()) {
            Pair<SymbolIdentity, ExtremaParams> stock = topStocks.poll();
            System.out.println(stock.getLeft() + ":");
            System.out.println("\tHighest correlation coefficient: " + stock.getRight().coefficient());
            System.out.println("\tOffset for highest correlation coefficient: " + stock.getRight().offset());
            System.out.println("\tBarCount for highest correlation coefficient: " + stock.getRight().barCount());
        }
    }

    public static ExtremaParams detectDecreasingVolatility(CandleSeries candleSeries) {
        PearsonsCorrelation pearsonsCorrelation = new PearsonsCorrelation();
        AtomicReference<ExtremaParams> minParams = new AtomicReference<>(new ExtremaParams(0, -1, -1));

        // Iterate from 3 to 90 for barCount
        IntStream.rangeClosed(3, 90).forEach(barCount -> {
            SimpleRegression regression = new SimpleRegression();

            // Slide window from 0 to 250 (maximum 1 year)
            IntStream.rangeClosed(0, 250 - barCount).forEach(offset -> {
                if (candleSeries.length() <= INDEX + offset + barCount)
                    return;

                // Collect data for correlation
                HighLowCandle highLow = candleSeries.getHighLow(INDEX + offset, INDEX + offset + barCount);
                regression.addData(offset /*+ barCount*/, highLow.range());

                if (offset >= 2 * barCount) {
                    // Calculate correlation coefficient
                    double correlationCoefficient = regression.getR();
                    // Update stats if needed
                    if (correlationCoefficient > minParams.get().coefficient()) {
                        minParams.set(new ExtremaParams(correlationCoefficient, offset, barCount));
                    }
                }
            });
        });

        return minParams.get();
//        System.out.println(candleSeries.getSymbol() + ":");
//        System.out.println("\tLowest correlation coefficient: " + minParams.get().coefficient());
//        System.out.println("\tOffset for lowest correlation coefficient: " + minParams.get().offset());
//        System.out.println("\tBarCount for lowest correlation coefficient: " + minParams.get().barCount());
    }

    record ExtremaParams(double coefficient, int offset, int barCount) { }
}
