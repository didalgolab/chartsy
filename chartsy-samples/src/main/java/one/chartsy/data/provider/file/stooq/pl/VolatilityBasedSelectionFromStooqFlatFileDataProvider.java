/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider.file.stooq.pl;

import one.chartsy.*;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DataQuery;
import one.chartsy.data.SeriesSummary;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.provider.file.FlatFileFormat;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

public class VolatilityBasedSelectionFromStooqFlatFileDataProvider {

    private static final Path PATH_TO_STOOQ_FILE = Path.of("C:/Downloads/d_pl_txt(3).zip");
//    private static final Path PATH_TO_STOOQ_FILE = Path.of("C:/Downloads/d_us_txt(1).zip");
    private static final Logger log = LogManager.getLogger(VolatilityBasedSelectionFromStooqFlatFileDataProvider.class);

    public static void main(String[] args) throws IOException {
        // create FlatFileDataProvider for a Stooq-based historical data file
        FlatFileDataProvider dataProvider = FlatFileFormat.STOOQ
                .newDataProvider(PATH_TO_STOOQ_FILE);

        // list all stocks contained in a file
        List<? extends SymbolIdentity> stocks = dataProvider.listSymbols(new SymbolGroup("/data/daily/pl/wse stocks"));
        int stockCount = stocks.size();
        log.info("Found {} stock(s)".replace("(s)", stockCount==1?"":"s"), stockCount);
        log.info("");

        // list summary of each stock data series
        int candleCount = 0;
        for (SymbolIdentity stock : stocks) {
            var resource = SymbolResource.of(stock, TimeFrame.Period.DAILY);
            var candles = dataProvider.queryForCandles(
                            DataQuery.of(resource))
                    .collectSortedList()
                    .as(CandleSeries.of(resource));
            detectDecreasingVolatility(candles);
            //log.info(candles);
        }
        log.info("Total {} candle(s)".replace("(s)", candleCount==1?"":"s"), candleCount);
    }

    public static void detectDecreasingVolatility(CandleSeries candleSeries) {
        PearsonsCorrelation pearsonsCorrelation = new PearsonsCorrelation();
        AtomicReference<ExtremaParams> minParams = new AtomicReference<>(new ExtremaParams(0, -1, -1));

        // Iterate from 3 to 90 for barCount
        IntStream.rangeClosed(3, 90).forEach(barCount -> {
            SimpleRegression regression = new SimpleRegression();

            // Slide window from 0 to 250 (maximum 1 year)
            IntStream.rangeClosed(0, 250 - barCount).forEach(offset -> {
                if (candleSeries.length() <= offset + barCount)
                    return;

                // Collect data for correlation
                HighLowCandle highLow = candleSeries.getHighLow(offset, offset + barCount);
                regression.addData(offset + barCount, highLow.range());

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

        System.out.println(candleSeries.getSymbol() + ":");
        System.out.println("\tLowest correlation coefficient: " + minParams.get().coefficient());
        System.out.println("\tOffset for lowest correlation coefficient: " + minParams.get().offset());
        System.out.println("\tBarCount for lowest correlation coefficient: " + minParams.get().barCount());
    }

    record ExtremaParams(double coefficient, int offset, int barCount) { }
}
