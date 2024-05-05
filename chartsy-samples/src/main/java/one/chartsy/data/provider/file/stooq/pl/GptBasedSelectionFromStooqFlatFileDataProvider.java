/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider.file.stooq.pl;

import one.chartsy.*;
import one.chartsy.core.collections.DoubleMinMaxList;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DataQuery;
import one.chartsy.data.DoubleSeries;
import one.chartsy.data.packed.PackedCandleSeries;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.finance.FinancialIndicators;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class GptBasedSelectionFromStooqFlatFileDataProvider {

    private static final int BAR_NO = 0;
    private static final int BARS_IN_YEAR = 250;
    private static final Path PATH_TO_STOOQ_FILE = Path.of("C:/Downloads/d_pl_txt(4).zip");
    private static final Logger log = LogManager.getLogger(GptBasedSelectionFromStooqFlatFileDataProvider.class);

    public static void main(String[] args) throws IOException {
        // create FlatFileDataProvider for a Stooq-based historical data file
        FlatFileDataProvider dataProvider = FlatFileFormat.STOOQ
                .newDataProvider(PATH_TO_STOOQ_FILE);

        // list all stocks contained in a file
        List<SymbolIdentity> stocks = dataProvider.listSymbols(new SymbolGroup("/data/daily/pl/wse stocks"));
        // Create a DecimalFormat that fits your requirements
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        symbols.setDecimalSeparator('.');
        DecimalFormat df = new DecimalFormat("0.00", symbols);

        // Sort stocks by turnover and print
        List<SymbolIdentity> sortedStocks = sortStocksByTurnover(stocks, dataProvider);
        sortedStocks.stream()
                .filter(sym -> sym.name().startsWith("PAT"))
                .forEach(System.out::println);

        // Generate Market data sheet
        for (SymbolIdentity stock : stocks) {
            var resource = SymbolResource.of(stock, TimeFrame.Period.DAILY);
            var candles = dataProvider.queryForCandles(
                            DataQuery.of(resource))
                    .collectSortedList()
                    .as(CandleSeries.of(resource));

            // Print out each candle's details
            if (candles.getSymbol().name().equals("MSZ")) {
                List<Candle> lastCandles = StreamSupport.stream(candles.spliterator(), false)
                        .skip(Math.max(0, candles.length() - 88))
                        .toList();
                for (Candle candle : lastCandles) {
                    System.out.printf("%s:{%s,%s,%s,%s,%s}%n",
                            candle.getDateTime().toLocalDate(),
                            df.format(candle.open()),
                            df.format(candle.high()),
                            df.format(candle.low()),
                            df.format(candle.close()),
                            (int) candle.volume());
                }
            }
        }
    }

    private static List<SymbolIdentity> sortStocksByTurnover(List<? extends SymbolIdentity> stocks, FlatFileDataProvider dataProvider) throws IOException {
        // Create a list of Stock objects
        List<Stock> stockList = new ArrayList<>();

        for (SymbolIdentity stock : stocks) {
            var resource = SymbolResource.of(stock, TimeFrame.Period.DAILY);
            var candles = dataProvider.queryForCandles(
                            DataQuery.of(resource))
                    .collectSortedList()
                    .as(CandleSeries.of(resource));

            // Calculate turnover and add Stock object to stockList
            if (!candles.isEmpty()) {
                Candle latestCandle = candles.get(0);
                double turnover = latestCandle.close() * latestCandle.volume();
                stockList.add(new Stock(stock, turnover));
            }
        }

        // Sort stockList by turnover in descending order
        stockList.sort(Comparator.comparing(Stock::getTurnover).reversed());

        // Convert sorted stockList to a List of SymbolIdentity
        return stockList.stream()
                .map(Stock::getSymbol)
                .toList();
    }

    private static class Stock {
        private final SymbolIdentity symbol;
        private final double turnover;

        public Stock(SymbolIdentity symbol, double turnover) {
            this.symbol = symbol;
            this.turnover = turnover;
        }

        public SymbolIdentity getSymbol() {
            return symbol;
        }

        public double getTurnover() {
            return turnover;
        }
    }
}
