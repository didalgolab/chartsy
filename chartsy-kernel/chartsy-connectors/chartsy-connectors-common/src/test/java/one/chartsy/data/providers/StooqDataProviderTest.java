/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.providers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import one.chartsy.Candle;
import one.chartsy.Symbol;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.DataQuery;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Disabled
class StooqDataProviderTest {

    @Test
    void parseVerificationChallenge_stooq_script_extracts_token_and_difficulty() throws Exception {
        String body = """
                <script>
                (async()=>{const c="challenge-token",d=1;await crypto.subtle.digest("SHA-256",new Uint8Array());await fetch("/__verify");})();
                </script>
                """;

        StooqDataProvider.VerificationChallenge challenge = StooqDataProvider.parseVerificationChallenge(body).orElseThrow();

        assertThat(challenge.token()).isEqualTo("challenge-token");
        assertThat(challenge.leadingZeroes()).isEqualTo(1);

        long nonce = StooqDataProvider.solveVerificationNonce(challenge);
        assertThat(sha256Hex(challenge.token() + nonce)).startsWith("0");
    }

    @Test
    void sendVerified_challenge_response_posts_nonce_and_retries_with_plain_cookie() throws Exception {
        try (VerificationTestServer server = new VerificationTestServer()) {
            StooqDataProvider provider = new StooqDataProvider();
            var request = HttpRequest.newBuilder(server.uri("/q/a2/d/?s=aapl.us&i=d"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            var response = provider.sendVerified(request);

            assertThat(response.body()).isEqualTo("Date,Time,Open,High,Low,Close,Volume\n20260619,,1,2,1,2,100\n");
            assertThat(server.dataRequestCount.get()).isEqualTo(2);
            assertThat(server.verifyRequestCount.get()).isEqualTo(1);
            assertThat(server.authenticatedRequestCookieHeader).isEqualTo("auth=ok");
        }
    }

    @Test
    void sendVerified_concurrent_challenges_share_one_verification_refresh() throws Exception {
        try (VerificationTestServer server = new VerificationTestServer(2);
             var executor = Executors.newFixedThreadPool(2)) {
            StooqDataProvider provider = new StooqDataProvider();
            var request = HttpRequest.newBuilder(server.uri("/q/a2/d/?s=aapl.us&i=d"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            var first = executor.submit(() -> provider.sendVerified(request).body());
            var second = executor.submit(() -> provider.sendVerified(request).body());

            assertThat(first.get()).startsWith("Date,Time,Open");
            assertThat(second.get()).startsWith("Date,Time,Open");
            assertThat(server.dataRequestCount.get()).isEqualTo(4);
            assertThat(server.verifyRequestCount.get()).isEqualTo(1);
        }
    }

    @Test
    void candleDataUri_a2_endpoint_uses_expected_interval_codes() {
        assertThat(StooqDataProvider.candleDataUri("atr", intervalCode(TimeFrame.Period.QUARTERLY)).toString())
                .isEqualTo("https://stooq.pl/q/a2/d/?s=atr&i=q");
        assertThat(intervalCode(TimeFrame.Period.MONTHLY)).isEqualTo("m");
        assertThat(intervalCode(TimeFrame.Period.WEEKLY)).isEqualTo("w");
        assertThat(intervalCode(TimeFrame.Period.DAILY)).isEqualTo("d");
        assertThat(intervalCode(TimeFrame.Period.H6)).isEqualTo("360");
        assertThat(intervalCode(TimeFrame.Period.H4)).isEqualTo("240");
        assertThat(intervalCode(TimeFrame.Period.H2)).isEqualTo("120");
        assertThat(intervalCode(TimeFrame.Period.H1)).isEqualTo("60");
        assertThat(intervalCode(TimeFrame.Period.M30)).isEqualTo("30");
        assertThat(intervalCode(TimeFrame.Period.M15)).isEqualTo("15");
        assertThat(intervalCode(TimeFrame.Period.M10)).isEqualTo("10");
        assertThat(intervalCode(TimeFrame.Period.M5)).isEqualTo("5");
        assertThat(intervalCode(TimeFrame.Period.M3)).isEqualTo("3");
        assertThat(intervalCode(TimeFrame.Period.M1)).isEqualTo("1");
    }

    @Test
    void parseCandles_a2_payload_accepts_chart_header_and_trailing_columns() throws Exception {
        String response = """
                <span id=f10><b>ATREM</b> (GPW: ATR)</span>
                20260820,000000,58.2,59.0,57.8,58.6,10123,0
                20260821,000000,58.6,61.5,58.0,59.9,11714,,Arrow
                """;

        List<Candle> candles = StooqDataProvider.parseCandles(response, TimeFrame.Period.DAILY);

        assertThat(candles).hasSize(2);
        assertThat(candles.getLast().open()).isEqualTo(58.6);
        assertThat(candles.getLast().high()).isEqualTo(61.5);
        assertThat(candles.getLast().low()).isEqualTo(58.0);
        assertThat(candles.getLast().close()).isEqualTo(59.9);
        assertThat(candles.getLast().volume()).isEqualTo(11714.0);
    }

    @Test
    void prewarmSession_live_session_supports_daily_and_weekly_queries() throws Exception {
        assumeTrue(Boolean.getBoolean("stooq.live"));
        StooqDataProvider provider = new StooqDataProvider();

        provider.prewarmSession();
        List<Candle> daily = fetchAtrCandles(provider, TimeFrame.Period.DAILY);
        List<Candle> weekly = fetchAtrCandles(provider, TimeFrame.Period.WEEKLY);

        assertThat(daily).isNotNull().hasSizeGreaterThan(100);
        assertThat(weekly).isNotNull().hasSizeGreaterThan(100);
        assertThat(daily.getLast().close()).isPositive();
        assertThat(weekly.getLast().close()).isPositive();
    }

    @Test
    void prewarmSession_establishes_session_reused_by_later_query() throws Exception {
        try (A2SessionTestServer server = new A2SessionTestServer(DataBehavior.VALID)) {
            StooqDataProvider provider = new StooqDataProvider(server.rootUri(), Duration.ZERO, Duration.ZERO);

            provider.prewarmSession();
            assertThat(server.dataRequestCount.get()).isZero();
            List<Candle> candles = fetchCandles(provider, "PKO", TimeFrame.Period.DAILY);

            assertThat(candles).hasSize(1);
            assertThat(server.chartSessionCount.get()).isEqualTo(1);
            assertThat(server.verifyRequestCount.get()).isEqualTo(1);
            assertThat(server.dataRequestCount.get()).isEqualTo(1);
        }
    }

    @Test
    void prewarmSession_concurrent_query_shares_one_session_bootstrap() throws Exception {
        try (A2SessionTestServer server = new A2SessionTestServer(DataBehavior.VALID);
             var executor = Executors.newFixedThreadPool(2)) {
            StooqDataProvider provider = new StooqDataProvider(
                    server.rootUri(), Duration.ofMillis(100), Duration.ZERO);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            var prewarmFuture = executor.submit(() -> {
                ready.countDown();
                start.await();
                provider.prewarmSession();
                return null;
            });
            var queryFuture = executor.submit(() -> {
                ready.countDown();
                start.await();
                return fetchCandles(provider, "PKO", TimeFrame.Period.DAILY);
            });
            boolean workersReady = ready.await(5, TimeUnit.SECONDS);
            start.countDown();

            assertThat(workersReady).isTrue();
            prewarmFuture.get(5, TimeUnit.SECONDS);
            assertThat(queryFuture.get(5, TimeUnit.SECONDS)).hasSize(1);
            assertThat(server.chartSessionCount.get()).isEqualTo(1);
            assertThat(server.verifyRequestCount.get()).isEqualTo(1);
            assertThat(server.dataRequestCount.get()).isEqualTo(1);
        }
    }

    @Test
    void prewarmSession_failed_bootstrap_allows_later_query_retry() throws Exception {
        try (A2SessionTestServer server = new A2SessionTestServer(DataBehavior.FAIL_INITIAL_VERIFICATION)) {
            StooqDataProvider provider = new StooqDataProvider(server.rootUri(), Duration.ZERO, Duration.ZERO);

            assertThatIOException()
                    .isThrownBy(provider::prewarmSession)
                    .withMessageContaining("HTTP status 503");
            List<Candle> candles = fetchCandles(provider, "PKO", TimeFrame.Period.DAILY);

            assertThat(candles).hasSize(1);
            assertThat(server.chartSessionCount.get()).isEqualTo(1);
            assertThat(server.verifyRequestCount.get()).isEqualTo(2);
            assertThat(server.dataRequestCount.get()).isEqualTo(1);
        }
    }

    @Test
    void fetchCandles_expired_a2_identity_reestablishes_session_once() throws Exception {
        try (A2SessionTestServer server = new A2SessionTestServer(DataBehavior.EXPIRE_INITIAL_IDENTITY)) {
            StooqDataProvider provider = new StooqDataProvider(server.rootUri(), Duration.ZERO, Duration.ZERO);

            List<Candle> candles = fetchAtrCandles(provider, TimeFrame.Period.DAILY);

            assertThat(candles).isNotNull().hasSize(1);
            assertThat(candles.getFirst().close()).isEqualTo(2.0);
            assertThat(server.chartSessionCount.get()).isEqualTo(2);
            assertThat(server.verifyRequestCount.get()).isEqualTo(2);
            assertThat(server.dataRequestCount.get()).isEqualTo(4);
            assertThat(server.expiredCookieWasReplayed.get()).isFalse();
        }
    }

    @Test
    void fetchCandles_empty_non_success_response_is_not_retried_or_rebootstrapped() throws Exception {
        try (A2SessionTestServer server = new A2SessionTestServer(DataBehavior.TOO_MANY_REQUESTS)) {
            StooqDataProvider provider = new StooqDataProvider(server.rootUri(), Duration.ZERO, Duration.ZERO);

            assertThatIOException()
                    .isThrownBy(() -> fetchAtrCandles(provider, TimeFrame.Period.DAILY))
                    .withMessageContaining("HTTP status 429");
            assertThat(server.chartSessionCount.get()).isEqualTo(1);
            assertThat(server.dataRequestCount.get()).isEqualTo(1);
        }
    }

    @Test
    void fetchCandles_concurrent_first_queries_share_one_session_bootstrap() throws Exception {
        try (A2SessionTestServer server = new A2SessionTestServer(DataBehavior.VALID);
             var executor = Executors.newFixedThreadPool(2)) {
            StooqDataProvider provider = new StooqDataProvider(server.rootUri(), Duration.ZERO, Duration.ZERO);
            CountDownLatch start = new CountDownLatch(1);

            var first = executor.submit(() -> {
                start.await();
                return fetchAtrCandles(provider, TimeFrame.Period.DAILY);
            });
            var second = executor.submit(() -> {
                start.await();
                return fetchAtrCandles(provider, TimeFrame.Period.DAILY);
            });
            start.countDown();

            assertThat(first.get()).hasSize(1);
            assertThat(second.get()).hasSize(1);
            assertThat(server.chartSessionCount.get()).isEqualTo(1);
            assertThat(server.verifyRequestCount.get()).isEqualTo(1);
        }
    }

    @Test
    void fetchCandles_concurrent_expired_identity_waits_for_one_replacement_session() throws Exception {
        try (A2SessionTestServer server = new A2SessionTestServer(DataBehavior.EXPIRE_INITIAL_IDENTITY);
             var executor = Executors.newFixedThreadPool(3)) {
            StooqDataProvider provider = new StooqDataProvider(
                    server.rootUri(), Duration.ofMillis(100), Duration.ZERO);
            CountDownLatch start = new CountDownLatch(1);

            var first = executor.submit(() -> {
                start.await();
                return fetchAtrCandles(provider, TimeFrame.Period.DAILY);
            });
            var second = executor.submit(() -> {
                start.await();
                return fetchAtrCandles(provider, TimeFrame.Period.DAILY);
            });
            var third = executor.submit(() -> {
                start.await();
                return fetchAtrCandles(provider, TimeFrame.Period.DAILY);
            });
            start.countDown();

            assertThat(first.get()).hasSize(1);
            assertThat(second.get()).hasSize(1);
            assertThat(third.get()).hasSize(1);
            assertThat(server.chartSessionCount.get()).isEqualTo(2);
            assertThat(server.verifyRequestCount.get()).isEqualTo(2);
        }
    }

    @Test
    void validateCandleResponse_rejects_empty_successful_response() {
        SymbolIdentity symbol = SymbolIdentity.of("AAPL.US");

        assertThatIOException()
                .isThrownBy(() -> StooqDataProvider.validateCandleResponse(200, "", symbol))
                .withMessageContaining("empty data response")
                .withMessageContaining(symbol.name());
    }

    @Test
    void validateCandleResponse_rejects_unexpected_html() {
        SymbolIdentity symbol = SymbolIdentity.of("AAPL.US");

        assertThatIOException()
                .isThrownBy(() -> StooqDataProvider.validateCandleResponse(
                        200, "<html><body>Access denied</body></html>", symbol))
                .withMessageContaining("HTML instead of candle data")
                .withMessageContaining(symbol.name());
    }

    @Test
    void validateCandleResponse_accepts_candle_data() throws Exception {
        String body = "<span id=f10><b>Apple</b></span>\n20260619,,1,2,1,2,100\n";

        assertThat(StooqDataProvider.validateCandleResponse(200, body, SymbolIdentity.of("AAPL.US"))).isSameAs(body);
    }

    @Test
    void parseAutocompletionResponse_current_payload_returns_symbols() {
        StooqDataProvider provider = new StooqDataProvider();
        String response = """
                window.cmp_r('<b>AAP</b>L.US~Apple Inc~XNAS~298.010~0.70%~3|<b>AAP</b>.US~Advance Auto Parts Inc~XNYS~60.1000~3.44%~4');
                """;

        List<Symbol> proposals = provider.parseAutocompletionResponse(response);

        assertThat(proposals).hasSize(2);
        assertThat(proposals.getFirst().name()).isEqualTo("AAPL.US");
        assertThat(proposals.getFirst().getDisplayName()).isEqualTo("Apple Inc");
        assertThat(proposals.getFirst().exchange()).isEqualTo("XNAS");
        assertThat(proposals.getFirst().lastPrice()).isEqualTo(298.010);
        assertThat(proposals.getFirst().dailyChangePercentage()).isEqualTo(0.70);
    }

    private static String sha256Hex(String text) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private static String intervalCode(TimeFrame timeFrame) {
        return StooqDataProvider.resolveInterval(timeFrame).orElseThrow().code();
    }

    private static List<Candle> fetchAtrCandles(StooqDataProvider provider, TimeFrame timeFrame)
            throws IOException, InterruptedException {
        return fetchCandles(provider, "ATR", timeFrame);
    }

    private static List<Candle> fetchCandles(StooqDataProvider provider, String symbol, TimeFrame timeFrame)
            throws IOException, InterruptedException {
        return provider.fetchCandles(DataQuery.of(SymbolResource.of(symbol, timeFrame)))
                .collectList()
                .block();
    }

    private static final class VerificationTestServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService serverExecutor = Executors.newCachedThreadPool();
        private final CountDownLatch challengeRequests;
        private final AtomicInteger dataRequestCount = new AtomicInteger();
        private final AtomicInteger verifyRequestCount = new AtomicInteger();
        private volatile String authenticatedRequestCookieHeader;

        private VerificationTestServer() throws IOException {
            this(1);
        }

        private VerificationTestServer(int concurrentChallenges) throws IOException {
            challengeRequests = new CountDownLatch(concurrentChallenges);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(serverExecutor);
            server.createContext("/q/a2/d/", this::handleData);
            server.createContext("/__verify", this::handleVerify);
            server.start();
        }

        private URI uri(String path) {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
        }

        private void handleData(HttpExchange exchange) throws IOException {
            dataRequestCount.incrementAndGet();
            List<String> cookieHeaders = exchange.getRequestHeaders().getOrDefault("Cookie", List.of());
            if (!cookieHeaders.contains("auth=ok")) {
                challengeRequests.countDown();
                try {
                    if (!challengeRequests.await(5, TimeUnit.SECONDS))
                        throw new IOException("Timed out waiting for concurrent challenge requests");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for concurrent challenge requests", e);
                }
                send(exchange, """
                        <!DOCTYPE html><html><body><script>
                        (async()=>{const c="local-test",d=1;await crypto.subtle.digest("SHA-256",new Uint8Array());await fetch("/__verify");})();
                        </script></body></html>
                        """);
                return;
            }

            authenticatedRequestCookieHeader = cookieHeaders.getFirst();
            send(exchange, "Date,Time,Open,High,Low,Close,Volume\n20260619,,1,2,1,2,100\n");
        }

        private void handleVerify(HttpExchange exchange) throws IOException {
            verifyRequestCount.incrementAndGet();
            exchange.getResponseHeaders().add("Set-Cookie", "auth=ok; Path=/");
            send(exchange, "");
        }

        private void send(HttpExchange exchange, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
            serverExecutor.shutdownNow();
        }
    }

    private enum DataBehavior {
        VALID,
        FAIL_INITIAL_VERIFICATION,
        EXPIRE_INITIAL_IDENTITY,
        TOO_MANY_REQUESTS
    }

    private static final class A2SessionTestServer implements AutoCloseable {
        private static final String CHALLENGE = """
                <!DOCTYPE html><html><body><script>
                (async()=>{const c="local-a2",d=1;await crypto.subtle.digest("SHA-256",new Uint8Array());await fetch("/__verify");})();
                </script></body></html>
                """;
        private static final String CANDLE_DATA = """
                <span id=f10><b>ATREM</b></span>
                20260619,,1,2,1,2,100
                """;

        private final HttpServer server;
        private final DataBehavior dataBehavior;
        private final AtomicInteger verifyRequestCount = new AtomicInteger();
        private final AtomicInteger chartSessionCount = new AtomicInteger();
        private final AtomicInteger dataRequestCount = new AtomicInteger();
        private final AtomicBoolean expiredCookieWasReplayed = new AtomicBoolean();

        private A2SessionTestServer(DataBehavior dataBehavior) throws IOException {
            this.dataBehavior = dataBehavior;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/q/a2/d/", this::handleData);
            server.createContext("/q/a2/", this::handleChart);
            server.createContext("/uu/", this::handleConsentImage);
            server.createContext("/__verify", this::handleVerify);
            server.start();
        }

        private URI rootUri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        }

        private void handleChart(HttpExchange exchange) throws IOException {
            if (!hasCookie(exchange, "auth", "ok")) {
                send(exchange, 200, CHALLENGE);
                return;
            }

            if (!hasCookie(exchange, "cookie_uu", "accepted")) {
                exchange.getResponseHeaders().add("Set-Cookie", "cookie_uu=pending; Path=/");
            } else if (hasCookie(exchange, "privacy", null)) {
                int sessionNumber = chartSessionCount.incrementAndGet();
                exchange.getResponseHeaders().add("Set-Cookie", "PHPSESSID=session-" + sessionNumber + "; Path=/");
                exchange.getResponseHeaders().add("Set-Cookie", "uid=user-" + sessionNumber + "; Path=/");
                exchange.getResponseHeaders().add("Set-Cookie", "cookie_user=identity-" + sessionNumber + "; Path=/");
            }
            send(exchange, 200, "chart");
        }

        private void handleConsentImage(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Set-Cookie", "cookie_uu=accepted; Path=/");
            send(exchange, 200, "image");
        }

        private void handleVerify(HttpExchange exchange) throws IOException {
            int requestNumber = verifyRequestCount.incrementAndGet();
            if (dataBehavior == DataBehavior.FAIL_INITIAL_VERIFICATION && requestNumber == 1) {
                send(exchange, 503, "");
                return;
            }
            exchange.getResponseHeaders().add("Set-Cookie", "auth=ok; Path=/");
            send(exchange, 200, "");
        }

        private void handleData(HttpExchange exchange) throws IOException {
            int requestNumber = dataRequestCount.incrementAndGet();
            if (dataBehavior == DataBehavior.TOO_MANY_REQUESTS) {
                send(exchange, 429, "");
                return;
            }

            if (dataBehavior != DataBehavior.EXPIRE_INITIAL_IDENTITY
                    && hasCookie(exchange, "cookie_user", "identity-1")) {
                send(exchange, 200, CANDLE_DATA);
            } else if (hasCookie(exchange, "cookie_user", "identity-1")) {
                if (requestNumber > 1)
                    expiredCookieWasReplayed.set(true);
                exchange.getResponseHeaders().add("Set-Cookie", "cookie_user=; Max-Age=0; Path=/");
                send(exchange, 200, "");
            } else if (hasCookie(exchange, "cookie_user", "identity-2")) {
                send(exchange, 200, CANDLE_DATA);
            } else {
                send(exchange, 200, "");
            }
        }

        private static boolean hasCookie(HttpExchange exchange, String name, String expectedValue) {
            for (String header : exchange.getRequestHeaders().getOrDefault("Cookie", List.of())) {
                for (String pair : header.split(";\\s*")) {
                    int separator = pair.indexOf('=');
                    if (separator > 0 && pair.substring(0, separator).equals(name)
                            && (expectedValue == null || pair.substring(separator + 1).equals(expectedValue)))
                        return true;
                }
            }
            return false;
        }

        private static void send(HttpExchange exchange, int statusCode, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0)
                exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
