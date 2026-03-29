package one.chartsy.bench.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
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
    void run_catalog_list_json_returns_seed_catalog_payload() throws Exception {
        CommandCapture capture = run("catalog", "list", "--format", "json");

        assertEquals(0, capture.exitCode());
        assertTrue(capture.errText().isBlank());

        JsonNode payload = objectMapper.readTree(capture.outText());

        assertEquals("ok", payload.path("status").asText());
        assertEquals("default", payload.path("catalogId").asText());
        assertEquals(1, payload.path("cases").size());
        assertEquals("double-tree-map", payload.path("cases").get(0).path("id").asText());
    }

    @Test
    void run_catalog_show_unknown_case_returns_structured_error() throws Exception {
        CommandCapture capture = run("catalog", "show", "--case", "missing-case", "--format", "json");

        assertEquals(1, capture.exitCode());
        assertTrue(capture.outText().isBlank());

        JsonNode payload = objectMapper.readTree(capture.errText());
        assertEquals("error", payload.path("status").asText());
        assertEquals("MISSING_CASE", payload.path("errorCode").asText());
        assertEquals("missing-case", payload.path("caseId").asText());
        assertEquals("classpath:/benchmark/catalog/default-catalog.json", payload.path("catalogSource").asText());
    }

    @Test
    void run_provide_unsupported_mode_returns_structured_error() throws Exception {
        CommandCapture capture = run("provide", "--case", "double-tree-map", "--mode", "banana", "--workspace", "tmp/workspace", "--json");

        assertEquals(1, capture.exitCode());
        assertTrue(capture.outText().isBlank());

        JsonNode payload = objectMapper.readTree(capture.errText());
        assertEquals("error", payload.path("status").asText());
        assertEquals("UNSUPPORTED_MODE", payload.path("errorCode").asText());
        assertEquals("provide", payload.path("command").asText());
        assertEquals("banana", payload.path("mode").asText());
    }

    @Test
    void agent_bench_snapshot_dependency_exposes_upstream_cli_entrypoint() {
        assertDoesNotThrow(() -> Class.forName("org.springaicommunity.bench.core.cli.BenchMain"));
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
