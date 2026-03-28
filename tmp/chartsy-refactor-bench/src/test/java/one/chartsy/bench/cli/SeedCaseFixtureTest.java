package one.chartsy.bench.cli;

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

class SeedCaseFixtureTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String snapshotCommit = "2d15766f6976fd5f515c60da8a9daed47080e5c9";
    private static final String snapshotObjectId = "b382051bf119ec187311d46e534b795688c92d93";
    private static final String sourcePath =
            "chartsy-kernel/chartsy-charting/src/main/java/one/chartsy/charting/util/DoubleTreeMap.java";

    @Test
    void catalog_list_default_catalog_contains_platform_seed_case() throws IOException {
        CommandCapture capture = run("catalog", "list", "--format", "json");

        assertEquals(0, capture.exitCode());
        assertTrue(capture.errText().isBlank());

        JsonNode payload = objectMapper.readTree(capture.outText());
        JsonNode cases = payload.path("cases");
        JsonNode caseSummary = cases.get(0);

        assertEquals("ok", payload.path("status").asText());
        assertEquals("default", payload.path("catalogId").asText());
        assertTrue(payload.path("seedOnly").asBoolean());
        assertFalse(payload.path("balancedV1Ready").asBoolean());
        assertEquals(1, cases.size());
        assertEquals("double-tree-map", caseSummary.path("id").asText());
        assertEquals("DoubleTreeMap platform seed", caseSummary.path("title").asText());
        assertEquals(snapshotCommit, caseSummary.path("lineage").path("snapshotCommit").asText());
        assertEquals(sourcePath, caseSummary.path("lineage").path("sourcePath").asText());
        assertTrue(payload.path("description").asText().contains("platform development"));
    }

    @Test
    void catalog_show_seed_case_exposes_lineage_scope_and_placeholder_references() throws IOException {
        CommandCapture capture = run("catalog", "show", "--case", "double-tree-map", "--format", "json");

        assertEquals(0, capture.exitCode());
        assertTrue(capture.errText().isBlank());

        JsonNode payload = objectMapper.readTree(capture.outText());
        JsonNode caseNode = payload.path("case");

        assertEquals("double-tree-map", caseNode.path("id").asText());
        assertEquals(snapshotCommit, caseNode.path("lineage").path("snapshotCommit").asText());
        assertEquals(snapshotObjectId, caseNode.path("lineage").path("snapshotObjectId").asText());
        assertEquals(sourcePath, caseNode.path("lineage").path("sourcePath").asText());
        assertEquals(sourcePath, caseNode.path("editScope").get(0).path("path").asText());
        assertTrue(caseNode.path("validationCommands").get(0).path("command").asText()
                .contains("chartsy-kernel/chartsy-charting"));
        assertEquals("placeholder", caseNode.path("goldReference").path("kind").asText());
        assertTrue(caseNode.path("goldReference").path("path").asText().endsWith("gold-placeholder.json"));
        assertEquals("placeholder", caseNode.path("noopReference").path("kind").asText());
        assertTrue(caseNode.path("noopReference").path("path").asText().endsWith("noop-placeholder.json"));
        assertFalse(caseNode.path("suiteClaims").path("finalBalancedV1").asBoolean());
        assertTrue(caseNode.path("suiteClaims").path("note").asText().contains("platform development"));
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
