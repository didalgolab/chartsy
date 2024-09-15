/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider.file.stooq.pl;

import one.chartsy.*;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DataQuery;
import one.chartsy.data.DoubleSeries;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.financial.ValueIndicatorSupport;
import one.chartsy.financial.indicators.FramaTrendWhispers;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class RuleBasedSelectionFromStooqFlatFileDataProvider {

    private static final int BAR_NO = 0;
    private static final int BARS_IN_YEAR = 250;
    private static final Path PATH_TO_STOOQ_FILE = Path.of("C:/Downloads/d_pl_txt(4).zip");
    private static final Logger log = LogManager.getLogger(RuleBasedSelectionFromStooqFlatFileDataProvider.class);

    public static void main(String[] args) throws IOException {
        // create FlatFileDataProvider for a Stooq-based historical data file
        FlatFileDataProvider dataProvider = FlatFileFormat.STOOQ
                .newDataProvider(PATH_TO_STOOQ_FILE);

        // list all stocks contained in a file
        List<SymbolIdentity> stocks = dataProvider.listSymbols(new SymbolGroup("/data/daily/pl/wse stocks"));
        Map<SymbolIdentity, Double> stockMeanFactors = new HashMap<>();
        for (SymbolIdentity stock : stocks) {
            var resource = SymbolResource.of(stock, TimeFrame.Period.DAILY);
            var candles = dataProvider.queryForCandles(
                            DataQuery.of(resource))
                    .collectSortedList()
                    .as(CandleSeries.of(resource));
            List<Double> factors = detectDecreasingVolatility(candles);
            double meanFactor = calculateMean(factors);
            stockMeanFactors.put(stock, meanFactor);
        }

        // sort stocks by mean factor in descending order and print top 20
        stockMeanFactors.entrySet().stream()
                .sorted(Map.Entry.<SymbolIdentity, Double>comparingByValue().reversed())
                .limit(20)
                .forEach(entry -> System.out.println("Stock: " + entry.getKey().name() + ", Mean Factor: " + entry.getValue()));
    }

    public static double calculateMean(List<Double> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Cannot calculate a geometric mean from an empty list.");
        }

        double product = 1.0;
        for (Double value : values) {
            if (value < 0) {
                throw new IllegalArgumentException("Geometric mean calculation requires positive numbers.");
            }
            product *= value;
        }

        return Math.pow(product, 1.0 / values.size());
    }

    public static List<Double> detectDecreasingVolatility(CandleSeries series) {
        List<Double> resultList = new ArrayList<>();
        if (series.length() <= BAR_NO)
            return List.of(0.0);

        DoubleSeries atrPercent = series.trueRange().sma(60).mul(2.0).div(series.closes());
        if (atrPercent.length() <= BAR_NO)
            return List.of(0.0);

        Candle current = series.get(BAR_NO);
        double currentOpen = current.open();
        double currentClose = current.close();
        double currentVolume = current.volume();
        double currentRange = current.close() / current.open();
        double yearlyHigh = Double.NEGATIVE_INFINITY, yearlyLow = Double.POSITIVE_INFINITY;
        int highestVolumeSince = 1;
        boolean highestVolumeSinceLocked = false;
        int highestRangeSince = 1;
        boolean highestRangeSinceLocked = false;
        int highestCloseSince = 1;
        boolean highestCloseSinceLocked = false;
        for (int barNo = BAR_NO; barNo < Math.min(series.length(), BAR_NO + BARS_IN_YEAR); barNo++) {
            Candle c = series.get(barNo);
            yearlyHigh = Math.max(yearlyHigh, c.high());
            yearlyLow = Math.min(yearlyLow, c.low());
            if (barNo == BAR_NO || c.volume() < currentVolume && !highestVolumeSinceLocked) {
                highestVolumeSince = barNo + 1 - BAR_NO;
            } else {
                highestVolumeSinceLocked = true;
            }
            if (barNo == BAR_NO || c.close() / c.open() < currentRange && !highestRangeSinceLocked) {
                highestRangeSince = barNo + 1 - BAR_NO;
            } else {
                highestRangeSinceLocked = true;
            }
            if (barNo == BAR_NO || c.high() < currentClose && !highestCloseSinceLocked) {
                highestCloseSince = barNo + 1 - BAR_NO;
            } else {
                highestCloseSinceLocked = true;
            }
        }

        // Calculate Yearly High-Low range %
        double yearlyHighLow = 0.0;
        if (!series.isEmpty()) {
            yearlyHighLow = (currentOpen - yearlyLow) / (yearlyHigh - yearlyLow);
        }
        resultList.add(yearlyHighLow);
        // Calculate Highest-volume-since mark
        double highestVolumeSinceMark = Math.log(1 + highestVolumeSince)/Math.log(1 + BARS_IN_YEAR);
        resultList.add(highestVolumeSinceMark);
        // Calculate Highest-range-since mark
        double highestRangeSinceMark = Math.log(1 + highestRangeSince)/Math.log(1 + BARS_IN_YEAR);
        resultList.add(highestRangeSinceMark);
        // Calculate volatility mark
        double atrp = atrPercent.get(BAR_NO);
        double volatilityMark = (atrp < 0.0333)? Math.sqrt(atrp * 30): (atrp > 0.10)? Math.sqrt(Math.min(1.0, atrp * 10 - 1.0)): 1.0;
        resultList.add(volatilityMark);
        // Calculate highest close since mark
        double highestCloseSinceMark = Math.log(1 + highestCloseSince)/Math.log(1 + BARS_IN_YEAR);
        resultList.add(highestCloseSinceMark);
        // Calculate Sfora width
        DoubleSeries width = ValueIndicatorSupport.calculate(series, new FramaTrendWhispers(), FramaTrendWhispers::getRange);
        double sforaMark = 1.0;
        if (width.length() > BAR_NO) {
            double currWidth = width.get(BAR_NO);
            int barCount = 0, wideCount = 0;
            for (int i = BAR_NO; i < Math.min(BAR_NO + BARS_IN_YEAR, width.length()); i++) {
                barCount++;
                if (width.get(i) >= currWidth)
                    wideCount++;
            }
            if (barCount > 0)
                sforaMark = Math.sqrt((double)wideCount / barCount);
        }
        resultList.add(sforaMark);

//        System.out.println(series.getSymbol() + ":");
//        System.out.println("\tYearly High-Low %: " + yearlyHighLow);
//        System.out.println("\tHighest Volume Mark: " + highestVolumeSinceMark);
//        System.out.println("\tHighest Range Mark: " + highestRangeSinceMark);
//        System.out.println("\tVolatility Mark %: " + volatilityMark);
//        System.out.println("\tHighest Close Mark: " + highestCloseSinceMark);
//        System.out.println("\tSfora Mark: " + sforaMark);
        return resultList;
    }

    record ExtremaParams(double coefficient, int offset, int barCount) { }
}
