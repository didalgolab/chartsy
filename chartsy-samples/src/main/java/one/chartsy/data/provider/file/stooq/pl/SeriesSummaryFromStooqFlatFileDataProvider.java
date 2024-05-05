/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider.file.stooq.pl;

import one.chartsy.*;
import one.chartsy.data.*;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.provider.file.FlatFileFormat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class SeriesSummaryFromStooqFlatFileDataProvider {

    private static final Logger log = LogManager.getLogger(SeriesSummaryFromStooqFlatFileDataProvider.class);

    public static void main(String[] args) throws IOException {
        // create FlatFileDataProvider for a Stooq-based historical data file
        FlatFileDataProvider dataProvider = FlatFileFormat.STOOQ
                .newDataProvider(Path.of("C:/Downloads/d_pl_txt(4).zip"));

        // list all stocks contained in a file
        List<SymbolIdentity> stocks = dataProvider.listSymbols(new SymbolGroup("/data/daily/pl/wse stocks"));
        int stockCount = stocks.size();
        log.info("Found {} stock(s)".replace("(s)", stockCount==1?"":"s"), stockCount);
        log.info("");

        // list summary of each stock data series
        int candleCount = 0;
        for (SymbolIdentity stock : stocks) {
            var resource = SymbolResource.of(stock, TimeFrame.Period.DAILY);
            var summary = dataProvider.queryForCandles(
                            DataQuery.of(resource))
                    .collectSortedList()
                    .as(CandleSeries.of(resource))
                    .query(SeriesSummary::new);
            candleCount += summary.getCount();
            log.info(summary);
        }
        log.info("Total {} candle(s)".replace("(s)", candleCount==1?"":"s"), candleCount);
    }
}
