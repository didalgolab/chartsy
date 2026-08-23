/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.providers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
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
    private static final Duration DEFAULT_A2_SESSION_MATURATION_DELAY = Duration.ofSeconds(5);
    private static final Duration DEFAULT_EMPTY_RESPONSE_RETRY_DELAY = Duration.ofSeconds(1);
    private static final int EMPTY_RESPONSE_RETRIES = 2;
    private static final URI DEFAULT_STOOQ_ROOT = URI.create("https://stooq.pl");
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36";
    private static final String AUTH_COOKIE_NAME = "auth";
    private static final String AUTH_COOKIE_PREFIX = AUTH_COOKIE_NAME + "=";
    private static final Pattern VERIFICATION_CHALLENGE_PATTERN = Pattern.compile("const\\s+c=\"([^\"]+)\",d=(\\d+)");
    private static final Pattern COOKIE_MAX_AGE_ZERO_PATTERN = Pattern.compile(
            "(?:^|;)\\s*Max-Age\\s*=\\s*0(?:;|$)", Pattern.CASE_INSENSITIVE);

    /** The Http Client used to execute the service requests. */
    private final HttpClient httpClient = newHttpClient();
    private final URI stooqRoot;
    private final Duration a2SessionMaturationDelay;
    private final Duration emptyResponseRetryDelay;
    private final Object verificationLock = new Object();
    private final Object a2SessionLock = new Object();
    private final SessionCookieJar sessionCookies = new SessionCookieJar();
    /** Guarded by {@link #a2SessionLock}; {@code null} means no usable bootstrap exists. */
    private A2Session a2Session;


    public StooqDataProvider() {
        this(DEFAULT_STOOQ_ROOT, DEFAULT_A2_SESSION_MATURATION_DELAY, DEFAULT_EMPTY_RESPONSE_RETRY_DELAY);
    }

    StooqDataProvider(URI stooqRoot, Duration a2SessionMaturationDelay, Duration emptyResponseRetryDelay) {
        super("Stooq");
        this.stooqRoot = stooqRoot;
        this.a2SessionMaturationDelay = a2SessionMaturationDelay;
        this.emptyResponseRetryDelay = emptyResponseRetryDelay;
        lookupContent.add(this);
    }

    protected HttpClient newHttpClient() {
        return newHttpClientBuilder().build();
    }

    protected HttpClient.Builder newHttpClientBuilder() {
        return HttpClient.newBuilder();
    }

    private static final Map<TimeFrame, String> INTERVAL_CODES = new LinkedHashMap<>();
    private static final List<TimeFrame> SUPPORTED_TIME_FRAMES;
    static {
        INTERVAL_CODES.put(TimeFrame.Period.QUARTERLY, "q");
        INTERVAL_CODES.put(TimeFrame.Period.MONTHLY, "m");
        INTERVAL_CODES.put(TimeFrame.Period.WEEKLY, "w");
        INTERVAL_CODES.put(TimeFrame.Period.DAILY, "d");
        INTERVAL_CODES.put(TimeFrame.Period.H6, "360");
        INTERVAL_CODES.put(TimeFrame.Period.H4, "240");
        INTERVAL_CODES.put(TimeFrame.Period.H2, "120");
        INTERVAL_CODES.put(TimeFrame.Period.H1, "60");
        INTERVAL_CODES.put(TimeFrame.Period.M30, "30");
        INTERVAL_CODES.put(TimeFrame.Period.M15, "15");
        INTERVAL_CODES.put(TimeFrame.Period.M10, "10");
        INTERVAL_CODES.put(TimeFrame.Period.M5, "5");
        INTERVAL_CODES.put(TimeFrame.Period.M3, "3");
        INTERVAL_CODES.put(TimeFrame.Period.M1, "1");
        SUPPORTED_TIME_FRAMES = List.copyOf(INTERVAL_CODES.keySet());
    }

    @Override
    public List<TimeFrame> getAvailableTimeFrames(SymbolIdentity symbol) {
        return SUPPORTED_TIME_FRAMES;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Chronological> Flux<T> query(Class<T> type, DataQuery<T> query) {
        try {
            return switch (type.getName()) {
                case "one.chartsy.Candle", "one.chartsy.data.SimpleCandle" -> (Flux<T>) fetchCandles((DataQuery<Candle>) query);
                default -> throw new DataProviderException("Unsupported data type: " + type.getSimpleName());
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataProviderException("Query interrupted", e);
        } catch (IOException e) {
            throw new DataProviderException("Query failed", e);
        }
    }

    //@Override
    public Flux<Candle> fetchCandles(DataQuery<Candle> query) throws IOException, InterruptedException {
        SymbolResource<Candle> resource = query.resource();
        String symbol = resource.symbol().name().toLowerCase();
        if (Currency.USD.equals(query.currency()) && !symbol.endsWith(".us"))
            symbol += ".us";
        String encodedSymbol = URLEncoder.encode(symbol, StandardCharsets.UTF_8);
        StooqInterval interval = resolveInterval(resource.timeFrame())
                .orElseThrow(() -> new IOException("Unsupported time frame: " + resource.timeFrame()));
        TimeFrame baseTimeFrame = interval.timeFrame();

        URI dataUri = candleDataUri(stooqRoot, encodedSymbol, interval.code());
        A2Session session = ensureA2Session(encodedSymbol);
        var response = requestCandleData(dataUri);
        if (isSuccessfulEmpty(response)) {
            recoverA2Session(encodedSymbol, session);
            response = requestCandleData(dataUri);
        }
        String responseBody = validateCandleResponse(response, resource.symbol());
        List<Candle> items = parseCandles(responseBody, baseTimeFrame);

        if (items.isEmpty())
            throw new IOException("Stooq returned no candle data for symbol `" + resource.symbol().name() + "`");

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
    }

    static Optional<StooqInterval> resolveInterval(TimeFrame timeFrame) {
        for (Map.Entry<TimeFrame, String> interval : INTERVAL_CODES.entrySet())
            if (timeFrame.isAssignableFrom(interval.getKey()))
                return Optional.of(new StooqInterval(interval.getKey(), interval.getValue()));

        return Optional.empty();
    }

    static URI candleDataUri(String encodedSymbol, String interval) {
        return candleDataUri(DEFAULT_STOOQ_ROOT, encodedSymbol, interval);
    }

    static URI chartUri(String encodedSymbol) {
        return chartUri(DEFAULT_STOOQ_ROOT, encodedSymbol);
    }

    private static URI candleDataUri(URI stooqRoot, String encodedSymbol, String interval) {
        return stooqRoot.resolve("/q/a2/d/?s=" + encodedSymbol + "&i=" + interval);
    }

    private static URI chartUri(URI stooqRoot, String encodedSymbol) {
        return stooqRoot.resolve("/q/a2/?s=" + encodedSymbol);
    }

    private A2Session ensureA2Session(String encodedSymbol) throws IOException, InterruptedException {
        while (true) {
            A2Session session;
            synchronized (a2SessionLock) {
                if (a2Session == null)
                    a2Session = establishA2Session(encodedSymbol);
                session = a2Session;
            }
            if (awaitA2SessionMaturation(session))
                return session;
        }
    }

    /** Performs the serialized browser bootstrap while {@link #a2SessionLock} is held. */
    private A2Session establishA2Session(String encodedSymbol) throws IOException, InterruptedException {
        URI chartUri = chartUri(stooqRoot, encodedSymbol);
        ensureVerificationCookie(chartUri);
        // q/a2/d remains empty until /uu advances cookie_uu and the resulting
        // cookie_user identity is about five seconds old.
        requireSuccessful("Stooq chart consent request", send(chartGet(chartUri)));
        requireSuccessful("Stooq chart consent image request", send(consentImageGet(stooqRoot.resolve("/uu/"), chartUri)));
        sessionCookies.put("privacy", Long.toString(System.currentTimeMillis() / 1_000));
        requireSuccessful("Stooq chart session request", send(chartGet(chartUri)));
        if (!sessionCookies.contains("cookie_user"))
            throw new IOException("Stooq did not establish a chart data session");

        return new A2Session(System.nanoTime() + a2SessionMaturationDelay.toNanos());
    }

    private void recoverA2Session(String encodedSymbol, A2Session failedSession) throws IOException, InterruptedException {
        synchronized (a2SessionLock) {
            if (a2Session == failedSession) {
                a2Session = null;
                sessionCookies.clear();
                a2Session = establishA2Session(encodedSymbol);
            }
        }
        ensureA2Session(encodedSymbol);
    }

    private HttpResponse<String> requestCandleData(URI dataUri) throws IOException, InterruptedException {
        var response = sendVerified(stooqGet(dataUri));
        for (int retry = 0; isSuccessfulEmpty(response) && retry < EMPTY_RESPONSE_RETRIES; retry++) {
            Thread.sleep(emptyResponseRetryDelay);
            response = sendVerified(stooqGet(dataUri));
        }
        return response;
    }

    private void ensureVerificationCookie(URI challengedUri) throws IOException, InterruptedException {
        if (sessionCookies.contains(AUTH_COOKIE_NAME))
            return;

        var response = send(stooqGet(challengedUri));
        var challenge = parseVerificationChallenge(response.body())
                .orElseThrow(() -> new IOException("Stooq did not return a browser verification challenge"));
        synchronized (verificationLock) {
            if (!sessionCookies.contains(AUTH_COOKIE_NAME))
                requestVerificationCookie(challengedUri, challenge);
        }
    }

    private boolean awaitA2SessionMaturation(A2Session session) throws InterruptedException {
        long remaining = session.readyAtNanos() - System.nanoTime();
        if (remaining > 0)
            TimeUnit.NANOSECONDS.sleep(remaining);
        synchronized (a2SessionLock) {
            return a2Session == session;
        }
    }

    private static boolean isEmpty(String body) {
        return body == null || body.isBlank();
    }

    private static boolean isSuccessfulEmpty(HttpResponse<String> response) {
        return isSuccessful(response.statusCode()) && isEmpty(response.body());
    }

    private static boolean isSuccessful(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private static void requireSuccessful(String requestName, HttpResponse<?> response) throws IOException {
        if (!isSuccessful(response.statusCode()))
            throw new IOException(requestName + " failed with HTTP status " + response.statusCode());
    }

    static List<Candle> parseCandles(String responseBody, TimeFrame baseTimeFrame) throws IOException {
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
        itemReader.setInputStreamSource(() -> new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8)));

        try {
            itemReader.open();
            return itemReader.readAll();
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
    
    static String validateCandleResponse(HttpResponse<String> response, SymbolIdentity symbol) throws IOException {
        return validateCandleResponse(response.statusCode(), response.body(), symbol);
    }

    static String validateCandleResponse(int statusCode, String body, SymbolIdentity symbol) throws IOException {
        if (!isSuccessful(statusCode))
            throw new IOException("Stooq data request failed with HTTP status " + statusCode);

        if (isEmpty(body))
            throw new IOException("Stooq returned an empty data response for symbol `" + symbol.name() + "`");

        String trimmedBody = body.strip();
        if ("-".equals(trimmedBody) || trimmedBody.contains("__nodata__"))
            throw new IOException("Symbol `" + symbol.name() + "` not found at Stooq");
        if (trimmedBody.startsWith("<") && !trimmedBody.startsWith("<span id=f10>"))
            throw new IOException("Stooq returned HTML instead of candle data for symbol `" + symbol.name() + "`");

        return body;
    }

    //@Override
    public List<Symbol> getProposals(String p) throws IOException, InterruptedException {
        var uri = stooqRoot.resolve("/cmp/?q=" + URLEncoder.encode(p, StandardCharsets.UTF_8));
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

    private static HttpRequest chartGet(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .header("Upgrade-Insecure-Requests", "1")
                .GET()
                .build();
    }

    private static HttpRequest consentImageGet(URI consentImageUri, URI referer) {
        return HttpRequest.newBuilder(consentImageUri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .header("Referer", referer.toString())
                .header("Sec-Fetch-Dest", "image")
                .header("Sec-Fetch-Mode", "no-cors")
                .header("Sec-Fetch-Site", "same-origin")
                .GET()
                .build();
    }

    HttpResponse<String> sendVerified(HttpRequest request) throws IOException, InterruptedException {
        long initialAuthRevision = sessionCookies.authRevision();
        var response = send(request);
        var challenge = parseVerificationChallenge(response.body());
        if (challenge.isEmpty())
            return response;

        synchronized (verificationLock) {
            if (sessionCookies.authRevision() == initialAuthRevision
                    || !sessionCookies.contains(AUTH_COOKIE_NAME))
                requestVerificationCookie(request.uri(), challenge.orElseThrow());
        }
        response = send(request);
        if (parseVerificationChallenge(response.body()).isPresent())
            throw new IOException("Stooq browser verification challenge was not accepted");
        return response;
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        var response = httpClient.send(withSessionCookies(request), MoreBodyHandlers.decoding(BodyHandlers.ofString()));
        sessionCookies.store(response.headers());
        return response;
    }

    private void requestVerificationCookie(URI challengedUri, VerificationChallenge challenge) throws IOException, InterruptedException {
        long nonce = solveVerificationNonce(challenge);
        var request = HttpRequest.newBuilder(challengedUri.resolve("/__verify"))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Origin", stooqRoot.toString())
                .header("Referer", challengedUri.toString())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(verificationForm(challenge, nonce)))
                .build();

        var verificationResponse = send(request);
        if (!isSuccessful(verificationResponse.statusCode()))
            throw new IOException("Stooq browser verification failed with HTTP status " + verificationResponse.statusCode());

        if (extractVerificationCookie(verificationResponse.headers()).isEmpty())
            throw new IOException("Stooq browser verification did not return an auth cookie");
    }

    private static String verificationForm(VerificationChallenge challenge, long nonce) {
        return "c=" + URLEncoder.encode(challenge.token(), StandardCharsets.UTF_8)
                + "&n=" + nonce;
    }

    private HttpRequest withSessionCookies(HttpRequest request) {
        String cookieHeader = sessionCookies.headerValue();
        if (cookieHeader.isEmpty() || request.headers().firstValue("Cookie").isPresent())
            return request;

        var builder = HttpRequest.newBuilder(request.uri());
        request.timeout().ifPresent(builder::timeout);
        request.version().ifPresent(builder::version);
        builder.expectContinue(request.expectContinue());
        request.headers().map().forEach((name, values) -> {
            if (!"Cookie".equalsIgnoreCase(name))
                values.forEach(value -> builder.header(name, value));
        });
        builder.header("Cookie", cookieHeader);
        builder.method(request.method(), request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));
        return builder.build();
    }

    private static final class SessionCookieJar {
        private final Map<String, String> cookies = new LinkedHashMap<>();
        private long authRevision;

        synchronized void store(HttpHeaders headers) {
            for (String header : headers.allValues("Set-Cookie")) {
                String cookie = firstCookiePair(header);
                int separator = cookie.indexOf('=');
                if (separator > 0) {
                    String name = cookie.substring(0, separator);
                    String value = cookie.substring(separator + 1);
                    if (value.isEmpty() || COOKIE_MAX_AGE_ZERO_PATTERN.matcher(header).find())
                        cookies.remove(name);
                    else
                        cookies.put(name, value);
                    if (AUTH_COOKIE_NAME.equals(name))
                        authRevision++;
                }
            }
        }

        synchronized void clear() {
            if (cookies.containsKey(AUTH_COOKIE_NAME))
                authRevision++;
            cookies.clear();
        }

        synchronized long authRevision() {
            return authRevision;
        }

        synchronized void put(String name, String value) {
            cookies.put(name, value);
        }

        synchronized boolean contains(String name) {
            return cookies.containsKey(name);
        }

        synchronized String headerValue() {
            var header = new StringJoiner("; ");
            cookies.forEach((name, value) -> header.add(name + "=" + value));
            return header.toString();
        }
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

    record StooqInterval(TimeFrame timeFrame, String code) {}

    private record A2Session(long readyAtNanos) {}
    
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
