/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.providers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

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
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36";
    private static final String AUTH_COOKIE_PREFIX = "auth=";
    private static final Pattern VERIFICATION_CHALLENGE_PATTERN = Pattern.compile("const\\s+c=\"([^\"]+)\",d=(\\d+)");

    /** The Http Client used to execute the service requests. */
    private final HttpClient httpClient = newHttpClient();
    private final Object verificationLock = new Object();
    private volatile String verificationCookie;


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

    private static final Map<TimeFrame, String> intervals = new LinkedHashMap<>();
    private static final List<TimeFrame> supportedTimeFrames;
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
        supportedTimeFrames = List.copyOf(intervals.keySet());
    }

    @Override
    public List<TimeFrame> getAvailableTimeFrames(SymbolIdentity symbol) {
        return supportedTimeFrames;
    }

    @SuppressWarnings("unchecked")
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
        var response = sendVerified(stooqGet(uri));
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

            //items.sort(Comparator.naturalOrder());
            if (query.endTime() != null) {
                long endTime = Chronological.toEpochNanos(query.endTime());
                items.removeIf(item -> item.time() > endTime);
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
        var response = sendVerified(stooqGet(uri));
        return parseAutocompletionResponse(response.body());
    }

    private static HttpRequest stooqGet(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
    }

    HttpResponse<String> sendVerified(HttpRequest request) throws IOException, InterruptedException {
        var response = send(withVerificationCookie(request));
        var challenge = parseVerificationChallenge(response.body());
        if (challenge.isEmpty())
            return response;

        synchronized (verificationLock) {
            verificationCookie = requestVerificationCookie(request.uri(), challenge.orElseThrow());
        }
        response = send(withVerificationCookie(request));
        if (parseVerificationChallenge(response.body()).isPresent())
            throw new IOException("Stooq browser verification challenge was not accepted");
        return response;
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return httpClient.send(request, MoreBodyHandlers.decoding(BodyHandlers.ofString()));
    }

    private String requestVerificationCookie(URI challengedUri, VerificationChallenge challenge) throws IOException, InterruptedException {
        long nonce = solveVerificationNonce(challenge);
        var request = HttpRequest.newBuilder(challengedUri.resolve("/__verify"))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(verificationForm(challenge, nonce)))
                .build();

        var verificationResponse = httpClient.send(request, BodyHandlers.discarding());
        if (verificationResponse.statusCode() < 200 || verificationResponse.statusCode() >= 300)
            throw new IOException("Stooq browser verification failed with HTTP status " + verificationResponse.statusCode());

        return extractVerificationCookie(verificationResponse.headers())
                .orElseThrow(() -> new IOException("Stooq browser verification did not return an auth cookie"));
    }

    private static String verificationForm(VerificationChallenge challenge, long nonce) {
        return "c=" + URLEncoder.encode(challenge.token(), StandardCharsets.UTF_8)
                + "&n=" + nonce;
    }

    private HttpRequest withVerificationCookie(HttpRequest request) {
        String cookie = verificationCookie;
        if (cookie == null || request.headers().firstValue("Cookie").isPresent())
            return request;

        var builder = HttpRequest.newBuilder(request.uri());
        request.timeout().ifPresent(builder::timeout);
        request.version().ifPresent(builder::version);
        builder.expectContinue(request.expectContinue());
        request.headers().map().forEach((name, values) -> {
            if (!"Cookie".equalsIgnoreCase(name))
                values.forEach(value -> builder.header(name, value));
        });
        builder.header("Cookie", cookie);
        builder.method(request.method(), request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));
        return builder.build();
    }

    static Optional<String> extractVerificationCookie(HttpHeaders headers) {
        for (String header : headers.allValues("Set-Cookie")) {
            String cookie = firstCookiePair(header);
            if (cookie.startsWith(AUTH_COOKIE_PREFIX) && cookie.length() > AUTH_COOKIE_PREFIX.length())
                return Optional.of(cookie);
        }
        return Optional.empty();
    }

    private static String firstCookiePair(String setCookieHeader) {
        int attributesStart = setCookieHeader.indexOf(';');
        return ((attributesStart < 0)? setCookieHeader : setCookieHeader.substring(0, attributesStart)).strip();
    }

    static Optional<VerificationChallenge> parseVerificationChallenge(String response) {
        if (response == null || !response.contains("/__verify") || !response.contains("crypto.subtle.digest"))
            return Optional.empty();

        var matcher = VERIFICATION_CHALLENGE_PATTERN.matcher(response);
        if (!matcher.find())
            return Optional.empty();
        return Optional.of(new VerificationChallenge(matcher.group(1), Integer.parseInt(matcher.group(2))));
    }

    static long solveVerificationNonce(VerificationChallenge challenge) throws IOException {
        if (challenge.leadingZeroes() < 0 || challenge.leadingZeroes() > 6)
            throw new IOException("Unsupported Stooq verification challenge difficulty: " + challenge.leadingZeroes());

        MessageDigest digest = sha256();
        String targetPrefix = "0".repeat(challenge.leadingZeroes());
        HexFormat hex = HexFormat.of();
        for (long nonce = 0; ; nonce++) {
            digest.reset();
            byte[] hash = digest.digest((challenge.token() + nonce).getBytes(StandardCharsets.UTF_8));
            if (hex.formatHex(hash).startsWith(targetPrefix))
                return nonce;
        }
    }

    private static MessageDigest sha256() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 digest is not available", e);
        }
    }

    record VerificationChallenge(String token, int leadingZeroes) {}
    
    List<Symbol> parseAutocompletionResponse(String response) {
        int i = response.indexOf('\''), j = response.lastIndexOf('\'');
        if (i < 0 || j < 0 || i == j)
            return List.of();

        response = response.substring(i + 1, j);
        response = response.replace("<b>", "");
        response = response.replace("</b>", "");

        List<Symbol> list = new ArrayList<>();
        String[] rows = response.split("\\|");
        for (String row : rows) {
            String[] c = row.split("~", -1);
            if (c.length <= 2)
                continue;

            var symb = new Symbol.Builder(SymbolIdentity.of(c[0]), this)
                    .displayName(c[1])
                    .exchange(c[2]);
            parseNumericColumn(c, 3).ifPresent(symb::lastPrice);
            parseNumericColumn(c, 4).ifPresent(symb::dailyChangePercentage);
            list.add(symb.build());
        }
        return list;
    }

    private static OptionalDouble parseNumericColumn(String[] columns, int index) {
        if (index >= columns.length)
            return OptionalDouble.empty();

        String column = columns[index];
        if (column == null)
            return OptionalDouble.empty();

        String value = column.strip().replace("%", "");
        if (value.isEmpty() || value.charAt(0) == '#')
            return OptionalDouble.empty();

        try {
            return OptionalDouble.of(Double.parseDouble(value));
        } catch (NumberFormatException ignored) {
            return OptionalDouble.empty();
        }
    }
}
