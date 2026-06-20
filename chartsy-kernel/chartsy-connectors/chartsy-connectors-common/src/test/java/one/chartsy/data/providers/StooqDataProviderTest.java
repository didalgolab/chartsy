/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.providers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import one.chartsy.Symbol;
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

import static org.assertj.core.api.Assertions.assertThat;

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
        try (TestServer server = new TestServer()) {
            StooqDataProvider provider = new StooqDataProvider();
            var request = HttpRequest.newBuilder(server.uri("/q/a2/d/?s=aapl.us&i=d"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            var response = provider.sendVerified(request);

            assertThat(response.body()).isEqualTo("Date,Time,Open,High,Low,Close,Volume\n20260619,,1,2,1,2,100\n");
            assertThat(server.dataRequestCount).isEqualTo(2);
            assertThat(server.verifyRequestCount).isEqualTo(1);
            assertThat(server.retryCookieHeader).isEqualTo("auth=ok");
        }
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

    private static class TestServer implements AutoCloseable {
        private final HttpServer server;
        private int dataRequestCount;
        private int verifyRequestCount;
        private String retryCookieHeader;

        private TestServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/q/a2/d/", this::handleData);
            server.createContext("/__verify", this::handleVerify);
            server.start();
        }

        private URI uri(String path) {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
        }

        private void handleData(HttpExchange exchange) throws IOException {
            dataRequestCount++;
            List<String> cookieHeaders = exchange.getRequestHeaders().getOrDefault("Cookie", List.of());
            if (!cookieHeaders.contains("auth=ok")) {
                send(exchange, """
                        <!DOCTYPE html><html><body><script>
                        (async()=>{const c="local-test",d=1;await crypto.subtle.digest("SHA-256",new Uint8Array());await fetch("/__verify");})();
                        </script></body></html>
                        """);
                return;
            }

            retryCookieHeader = cookieHeaders.getFirst();
            send(exchange, "Date,Time,Open,High,Low,Close,Volume\n20260619,,1,2,1,2,100\n");
        }

        private void handleVerify(HttpExchange exchange) throws IOException {
            verifyRequestCount++;
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
        }
    }
}
