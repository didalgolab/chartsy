package one.chartsy.data.provider.file.stooq.pl;

import one.chartsy.*;
import one.chartsy.data.*;
import one.chartsy.data.batch.Batches;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.provider.file.FlatFileFormat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class SeriesSummaryFromStooqFlatFileDataProvider {

    public static void main(String[] args) throws IOException {
        // create FlatFileDataProvider for a Stooq-based historical data file
        FlatFileDataProvider dataProvider = FlatFileFormat.STOOQ
                .newDataProvider(Path.of("C:/Users/Mariusz/Downloads/d_pl_txt(4).zip"));

        // list all stocks contained in a file
        List<? extends SymbolIdentity> stocks = dataProvider.listSymbols(new SymbolGroup("/data/daily/pl/wse stocks"));
        int stockCount = stocks.size();
        System.out.printf("Found %d stock(s)".replace("(s)", stockCount==1?"":"s"), stockCount);
        System.out.println();

        // list summary of each stock data series
        int candleCount = 0;
        for (SymbolIdentity stock : stocks) {
            var summary = dataProvider.queryForCandles(
                            DataQuery.of(SymbolResource.of(stock, TimeFrame.Period.DAILY)))
                    .collect(Batches.toCandleSeries())
                    .query(SeriesSummary::new);
            candleCount += summary.getCount();
            System.out.println(summary);
        }
        System.out.printf("Total %d candle(s)".replace("(s)", candleCount==1?"":"s"), candleCount);
    }
}
