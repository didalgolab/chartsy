/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.providers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import com.github.mizosoft.methanol.MoreBodyHandlers;
import one.chartsy.*;
import one.chartsy.Currency;
import one.chartsy.context.ExecutionContext;
import one.chartsy.data.DataQuery;
import one.chartsy.data.provider.AbstractDataProvider;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.data.provider.DataProviderException;
import one.chartsy.data.provider.SymbolProposalProvider;
import one.chartsy.data.provider.file.*;
import one.chartsy.time.Chronological;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import reactor.core.publisher.Flux;

import static one.chartsy.TimeFrameHelper.isIntraday;

/**
 * Connects with the stooq.pl and fetches data from the website.
 * 
 * @author Mariusz Bernacki
 */
@ServiceProviders({
    @ServiceProvider(service = DataProvider.class, position = 0),
    @ServiceProvider(service = StooqDataProvider.class)
})
public class StooqDataProvider extends AbstractDataProvider implements SymbolProposalProvider {
    /** The serial version UID */
    private static final long serialVersionUID = 2170493974406373183L;
    /** The namespace constant used by this data provider. */
    public static final String NAMESPACE = "http://stooq.com";
    /** The Http Client used to execute the service requests. */
    private final HttpClient httpClient = newHttpClient();


    public StooqDataProvider() {
        super("Stooq");
        lookupContent.add(this);
    }

    protected HttpClient newHttpClient() {
        return newHttpClientBuilder().build();
    }

    protected HttpClient.Builder newHttpClientBuilder() {
        return HttpClient.newBuilder();
    }

    /**
     * Returns the base time frame in which the given symbol is represented.
     * 
     * @return the base time frame supported
     */
    public TimeFrame getBaseTimeFrame(Symbol symbol) {
        return TimeFrame.Period.M1;
    }
    
    private static Map<TimeFrame, String> intervals = new LinkedHashMap<>();
    static {
        intervals.put(TimeFrame.Period.QUARTERLY, "q");
        intervals.put(TimeFrame.Period.MONTHLY, "m");
        intervals.put(TimeFrame.Period.WEEKLY, "w");
        intervals.put(TimeFrame.Period.DAILY, "d");
        intervals.put(TimeFrame.Period.H6, "360");
        intervals.put(TimeFrame.Period.H4, "240");
        intervals.put(TimeFrame.Period.H2, "120");
        intervals.put(TimeFrame.Period.H1, "60");
        intervals.put(TimeFrame.Period.M30, "30");
        intervals.put(TimeFrame.Period.M15, "15");
        intervals.put(TimeFrame.Period.M10, "10");
        intervals.put(TimeFrame.Period.M5, "5");
        intervals.put(TimeFrame.Period.M3, "3");
        intervals.put(TimeFrame.Period.M1, "1");
    }

    @Override
    public <T extends Chronological> Flux<T> query(Class<T> type, DataQuery<T> query) {
        try {
            return switch (type.getName()) {
                case "one.chartsy.Candle", "one.chartsy.data.SimpleCandle" -> (Flux<T>) fetchCandles((DataQuery<Candle>) query);
                default -> throw new DataProviderException("Unsupported data type: " + type.getSimpleName());
            };
        } catch (IOException | InterruptedException e) {
            throw new DataProviderException("Query failed", e);
        }
    }

    //@Override
    public Flux<Candle> fetchCandles(DataQuery<Candle> query) throws IOException, InterruptedException {
        SymbolResource<Candle> resource = query.resource();
        String sym = resource.symbol().name().toLowerCase();
        if (query.currency() != null) {
            if (Currency.USD.equals(query.currency()) && !sym.endsWith(".us"))
                sym += ".us";
        }
        sym = URLEncoder.encode(sym, StandardCharsets.UTF_8);
        String itv = null;
        TimeFrame baseTimeFrame = null;
        for (Map.Entry<TimeFrame, String> interval : intervals.entrySet())
            if (resource.timeFrame().isAssignableFrom(interval.getKey())) {
                baseTimeFrame = interval.getKey();
                itv = interval.getValue();
                break;
            }
        if (baseTimeFrame == null)
            throw new IOException("Unsupported time frame: " + resource.timeFrame());

        var uri = URI.create("https://stooq.pl/q/a2/d/?s=" + sym + "&i=" + itv);
        var request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:34.0) Gecko/20100101 Firefox/34.0")
                .GET()
                .build();

        var response = httpClient.send(request, MoreBodyHandlers.decoding(BodyHandlers.ofString()));
        var execContext = new ExecutionContext();
        var fileFormat = FlatFileFormat.builder()
                .lineMapper(new SimpleCandleLineMapper.Type(
                        ',', List.of("DATE",(isIntraday(baseTimeFrame)? "TIME":"SKIP"),"OPEN","HIGH","LOW","CLOSE","VOLUME?"),
                                DateTimeFormatter.ofPattern("['*']yyyyMMdd"), DateTimeFormatter.ofPattern("HHmmss")))
                .skipFirstLines(1)
                //.setSkipLinesMatcher(line -> line.startsWith("Date,Time,"))
                //.setAcceptTooLongLines(true);
                //.setOptionalColumns(1);
                .build();

        FlatFileItemReader<Candle> itemReader = new FlatFileItemReader<>();
        itemReader.setLineMapper((LineMapper<Candle>) fileFormat.getLineMapper().createLineMapper(execContext));
        itemReader.setLinesToSkip(fileFormat.getSkipFirstLines());
        itemReader.setInputStreamSource(() -> new ByteArrayInputStream(response.body().getBytes(StandardCharsets.UTF_8)));

        try {
            itemReader.open();
            List<Candle> items = itemReader.readAll();

            System.out.println(items);

            //items.sort(Comparator.naturalOrder());
            if (query.endTime() != null) {
                long endTime = Chronological.toEpochNanos(query.endTime());
                items.removeIf(item -> item.getTime() > endTime);
            }

            int itemCount = items.size();
            int itemLimit = query.limit();
            if (itemLimit > 0 && itemLimit < itemCount)
                items = items.subList(itemCount - itemLimit, itemCount);

            return Flux.fromIterable(items);

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            itemReader.close();
        }
    }

    /** this data provider's data time zone. */
    private static final ZoneId dataTimeZone = ZoneId.of("Europe/Warsaw");
//    /** This data provider's candlestick alignment. */
//    private static final Optional<CandleAlignment> candleAlignment = Optional.of(new CandleAlignment(dataTimeZone));
//
//    @Override
//    public Optional<CandleAlignment> getCandleAlignment(SymbolExt symbol) {
//        return candleAlignment;
//    }
    
    private static void maybeSymbolNotFound(Symbol symbol, List<String> skippedLines) throws IOException {
        if (skippedLines.size() == 1 && "-".equals(skippedLines.get(0)))
            throw new IOException("Symbol `" + symbol + "` not found");
    }

    //@Override
    public List<Symbol> getProposals(String p) throws IOException, InterruptedException {
        var uri = URI.create("https://stooq.pl/cmp/?q=" + URLEncoder.encode(p, StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:34.0) Gecko/20100101 Firefox/34.0")
                .GET()
                .build();

        var response = httpClient.send(request, BodyHandlers.ofString());
        return parseAutocompletionResponse(response.body());
    }
    
    private List<Symbol> parseAutocompletionResponse(String response) {
        int i = response.indexOf('\''), j = response.lastIndexOf('\'');
        if (i < 0 || j < 0 || i == j)
            return List.of();

        response = response.substring(i + 1, j);
        response = response.replace("<b>", "");
        response = response.replace("</b>", "");

        List<Symbol> list = new ArrayList<>();
        String[] rows = response.split("\\|");
        for (String row : rows) {
            String[] c = row.split("~");
            if (c.length == 0)
                continue;

            var symb = new Symbol.Builder(SymbolIdentity.of(c[0]), this)
                    .displayName(c[1])
                    .exchange(c[2]);
            if (c.length > 3 && c[3] != null && !c[3].isEmpty())
                symb = symb.lastPrice(Double.parseDouble(c[3]));
            if (c.length > 4 && c[4] != null && !c[4].isEmpty())
                symb = symb.dailyChangePercentage(Double.parseDouble(c[4].replace('%',' ')));
            list.add(symb.build());
        }
        return list;
    }

//    @Override
//    public final String getNamespace() {
//        return NAMESPACE;
//    }
    
    
    public static void main(String[] args) throws Exception {
        StooqDataProvider provider = new StooqDataProvider();
        List<Symbol> proposals = provider.getProposals("AB");

        DataQuery<Candle> query = DataQuery
                .resource(SymbolResource.of("EURUSD", TimeFrame.Period.DAILY))
                .build();
//        provider.getCandles(query);

//        Quotes quotes = new StooqDataProvider().getQuotes(new SymbolExt("EURUSD"), TimeFrame.Period.H1, null, null, null);
//
//        System.out.println(quotes.length());
//        for (Quote quote : quotes)
//            System.out.println(quote);
    }
}
