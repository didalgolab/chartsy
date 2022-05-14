/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider.file.stooq.pl;

import one.chartsy.*;
import one.chartsy.data.*;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CandleSeriesResampleTestOnStooqFlatFileDataProvider {

    private static final Logger log = LogManager.getLogger(CandleSeriesResampleTestOnStooqFlatFileDataProvider.class);

    public static void main(String[] args) throws IOException {
        FlatFileDataProvider dataProvider = FlatFileFormat.STOOQ
                .newDataProvider(Path.of("C:/Downloads/d_pl_txt(6).zip"));

        Map<Pair<Double, String>, String> counts = new TreeMap<>();
        List<? extends SymbolIdentity> stocks = dataProvider.listSymbols(new SymbolGroup("/data/daily/pl/wse stocks"));
        log.info("Stocks: {}", stocks.size());
        for (SymbolIdentity stock : stocks) {
            DataQuery<Candle> query = DataQuery.resource(SymbolResource.of(stock, TimeFrame.Period.DAILY))
                    .limit(500)
                    .build();
            CandleSeries series = dataProvider.queryForCandles(query)
                    .collectSortedList()
                    .as(CandleSeries.of(query.resource()));

            if (series.length() == 0) {
                log.info("Empty series: {}", stock);
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

            log.info("" + stock.name() + ": " + (cnt*10_000L/n)/100.0 + " %");
        }
        counts.forEach((k,v) -> log.info("# {}: {}", k, v));
    }
}
