package one.chartsy.bench.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class BenchmarkCatalog {

    private static final String defaultCatalogResource = "/benchmark/catalog/default-catalog.json";

    private final CatalogDescriptor descriptor;
    private final List<BenchmarkCase> cases;
    private final Map<String, BenchmarkCase> casesById;

    private BenchmarkCatalog(CatalogDescriptor descriptor, List<BenchmarkCase> cases, Map<String, BenchmarkCase> casesById) {
        this.descriptor = descriptor;
        this.cases = cases;
        this.casesById = casesById;
    }

    public static BenchmarkCatalog loadDefault(ObjectMapper objectMapper) {
        CatalogDescriptor descriptor = readJson(objectMapper, defaultCatalogResource, CatalogDescriptor.class);
        Map<String, BenchmarkCase> casesById = new LinkedHashMap<>();
        for (String caseResource : descriptor.caseResources()) {
            BenchmarkCase benchmarkCase = readJson(objectMapper, caseResource, BenchmarkCase.class);
            BenchmarkCase previousCase = casesById.put(benchmarkCase.id(), benchmarkCase);
            if (previousCase != null)
                throw new IllegalStateException("Duplicate benchmark case id: " + benchmarkCase.id());
        }
        return new BenchmarkCatalog(descriptor, List.copyOf(casesById.values()), Map.copyOf(casesById));
    }

    public String catalogId() {
        return descriptor.catalogId();
    }

    public String catalogVersion() {
        return descriptor.catalogVersion();
    }

    public String purpose() {
        return descriptor.purpose();
    }

    public boolean seedOnly() {
        return descriptor.seedOnly();
    }

    public boolean balancedV1Ready() {
        return descriptor.balancedV1Ready();
    }

    public String description() {
        return descriptor.description();
    }

    public String catalogSource() {
        return "classpath:" + defaultCatalogResource;
    }

    public List<CaseListEntry> listEntries() {
        return cases.stream()
                .map(BenchmarkCase::toListEntry)
                .toList();
    }

    public BenchmarkCase findCase(String caseId) {
        return casesById.get(caseId);
    }

    private static <T> T readJson(ObjectMapper objectMapper, String resourcePath, Class<T> type) {
        try (InputStream input = BenchmarkCatalog.class.getResourceAsStream(resourcePath)) {
            if (input == null)
                throw new IllegalStateException("Missing benchmark resource: " + resourcePath);
            return objectMapper.readValue(input, type);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read benchmark resource " + resourcePath, e);
        }
    }

    private record CatalogDescriptor(
            String catalogId,
            String catalogVersion,
            String purpose,
            boolean seedOnly,
            boolean balancedV1Ready,
            String description,
            List<String> caseResources) {
    }

    public record BenchmarkCase(
            String id,
            String title,
            String summary,
            String dominantIntent,
            SuiteClaims suiteClaims,
            Lineage lineage,
            Taxonomy taxonomy,
            Prioritization prioritization,
            List<EditScopeEntry> editScope,
            List<Invariant> invariants,
            List<ValidationCommand> validationCommands,
            ArtifactReference goldReference,
            ArtifactReference noopReference,
            EffectiveScoring effectiveScoring) {

        public CaseListEntry toListEntry() {
            return new CaseListEntry(id, title, summary, dominantIntent, suiteClaims, lineage, taxonomy, prioritization);
        }
    }

    public record CaseListEntry(
            String id,
            String title,
            String summary,
            String dominantIntent,
            SuiteClaims suiteClaims,
            Lineage lineage,
            Taxonomy taxonomy,
            Prioritization prioritization) {
    }

    public record SuiteClaims(boolean finalBalancedV1, String note) {
    }

    public record Lineage(
            String repository,
            String module,
            String sourcePath,
            String snapshotCommit,
            String snapshotCommitAbbrev,
            String snapshotObjectId,
            String snapshotRef,
            String historyNote) {
    }

    public record Taxonomy(String category, List<String> tags) {
    }

    public record Prioritization(String lane, int rank, String rationale) {
    }

    public record EditScopeEntry(String path, String kind, String role) {
    }

    public record Invariant(String id, String description) {
    }

    public record ValidationCommand(String id, String command, String workingDirectory, String purpose) {
    }

    public record ArtifactReference(String kind, String path, String label, String status, String note) {
    }

    public record EffectiveScoring(String caseSpecificOverrides, String note) {
    }
}
