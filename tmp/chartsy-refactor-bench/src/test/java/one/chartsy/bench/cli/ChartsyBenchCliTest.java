package one.chartsy.bench.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ChartsyBenchCliTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void run_no_arguments_prints_usage_and_fails() {
        CommandCapture capture = run();

        assertEquals(1, capture.exitCode());
        assertTrue(capture.errText().contains("Usage: chartsy-refactor-bench"));
        assertTrue(capture.errText().contains("catalog list"));
    }

    @Test
    void run_catalog_list_json_returns_scaffold_payload() throws IOException {
        CommandCapture capture = run("catalog", "list", "--format", "json");

        assertEquals(0, capture.exitCode());
        assertTrue(capture.errText().isBlank());
        assertEquals(expectedCatalogPayload(), objectMapper.readTree(capture.outText()));
    }

    @Test
    void run_provide_json_returns_structured_not_yet_implemented_error() throws IOException {
        CommandCapture capture = run("provide", "--case", "double-tree-map", "--mode", "gold", "--workspace", "tmp/workspace", "--json");

        assertEquals(2, capture.exitCode());
        assertTrue(capture.outText().isBlank());

        JsonNode payload = objectMapper.readTree(capture.errText());
        assertEquals("error", payload.path("status").asText());
        assertEquals("NOT_YET_IMPLEMENTED", payload.path("errorCode").asText());
        assertEquals("provide", payload.path("command").asText());
        assertFalse(payload.path("message").asText().isBlank());
    }

    @Test
    void agent_bench_snapshot_dependency_exposes_upstream_cli_entrypoint() {
        assertDoesNotThrow(() -> Class.forName("org.springaicommunity.bench.core.cli.BenchMain"));
    }

    private static JsonNode expectedCatalogPayload() throws IOException {
        try (var input = ChartsyBenchCliTest.class.getResourceAsStream("/contracts/scaffold/catalog-list.expected.json")) {
            return objectMapper.readTree(input);
        }
    }

    private static CommandCapture run(String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ChartsyBenchCli cli = new ChartsyBenchCli(new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));

        int exitCode = cli.run(args);
        return new CommandCapture(exitCode,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    private record CommandCapture(int exitCode, String outText, String errText) {
    }
}
