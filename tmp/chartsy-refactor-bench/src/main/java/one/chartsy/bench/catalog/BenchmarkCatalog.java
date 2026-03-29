package one.chartsy.bench.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class BenchmarkCatalog {

    private static final String defaultCatalogResource = "/benchmark/catalog/default-catalog.json";

    private final CatalogDescriptor descriptor;
    private final List<BenchmarkCase> cases;
    private final Map<String, BenchmarkCase> casesById;
    private final String catalogSource;

    private BenchmarkCatalog(
            CatalogDescriptor descriptor,
            List<BenchmarkCase> cases,
            Map<String, BenchmarkCase> casesById,
            String catalogSource) {
        this.descriptor = descriptor;
        this.cases = cases;
        this.casesById = casesById;
        this.catalogSource = catalogSource;
    }

    public static BenchmarkCatalog loadDefault(ObjectMapper objectMapper) {
        return loadCatalog(objectMapper, new ClasspathCatalogSource(defaultCatalogResource));
    }

    public static BenchmarkCatalog load(Path catalogPath, ObjectMapper objectMapper) {
        return loadCatalog(objectMapper, new FileSystemCatalogSource(catalogPath));
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
        return catalogSource;
    }

    public List<CaseListEntry> listEntries() {
        return cases.stream()
                .map(BenchmarkCase::toListEntry)
                .toList();
    }

    public BenchmarkCase findCase(String caseId) {
        return casesById.get(caseId);
    }

    private static BenchmarkCatalog loadCatalog(ObjectMapper objectMapper, CatalogSource catalogSource) {
        CatalogDescriptor descriptor = catalogSource.readDescriptor(objectMapper);
        Map<String, BenchmarkCase> casesById = new LinkedHashMap<>();
        for (String caseReference : descriptor.caseResources()) {
            LoadedCase loadedCase = catalogSource.readCase(caseReference, objectMapper);
            try {
                validateCase(loadedCase.catalogCase(), loadedCase.caseSource());
            } catch (InvalidCaseSchemaException e) {
                throw new InvalidCaseSchemaException(
                        catalogSource.catalogSource(),
                        e.caseSource(),
                        e.caseId(),
                        e.issues());
            }
            BenchmarkCase previousCase = casesById.put(loadedCase.catalogCase().id(), loadedCase.catalogCase());
            if (previousCase != null)
                throw new CatalogLoadingException(
                        "Duplicate benchmark case id: " + loadedCase.catalogCase().id(),
                        catalogSource.catalogSource());
        }
        return new BenchmarkCatalog(
                descriptor,
                List.copyOf(casesById.values()),
                Map.copyOf(casesById),
                catalogSource.catalogSource());
    }

    private static void validateCase(BenchmarkCase catalogCase, String caseSource) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (catalogCase == null) {
            issues.add(new ValidationIssue("case", "Case definition is missing"));
            throw new InvalidCaseSchemaException("unknown", caseSource, "unknown", List.copyOf(issues));
        }
        if (isBlank(catalogCase.id()))
            issues.add(new ValidationIssue("id", "Case id must not be blank"));

        if (catalogCase.lineage() == null) {
            issues.add(new ValidationIssue("lineage", "Lineage block is required"));
        } else {
            if (isBlank(catalogCase.lineage().snapshotCommit()))
                issues.add(new ValidationIssue("snapshotCommit", "Snapshot commit must not be blank"));
            if (isBlank(catalogCase.lineage().sourcePath()))
                issues.add(new ValidationIssue("lineage.sourcePath", "Lineage source path must not be blank"));
        }

        if (catalogCase.editScope() == null || catalogCase.editScope().isEmpty()) {
            issues.add(new ValidationIssue("editScope", "At least one edit-scope entry is required"));
        } else {
            for (int index = 0; index < catalogCase.editScope().size(); index++) {
                EditScopeEntry editScopeEntry = catalogCase.editScope().get(index);
                if (editScopeEntry == null || isBlank(editScopeEntry.path()))
                    issues.add(new ValidationIssue("editScope[" + index + "].path", "Edit-scope path must not be blank"));
            }
        }

        if (catalogCase.validationCommands() == null || catalogCase.validationCommands().isEmpty()) {
            issues.add(new ValidationIssue("validationCommands", "At least one validation command is required"));
        } else {
            for (int index = 0; index < catalogCase.validationCommands().size(); index++) {
                ValidationCommand validationCommand = catalogCase.validationCommands().get(index);
                if (validationCommand == null || isBlank(validationCommand.command()))
                    issues.add(new ValidationIssue(
                            "validationCommands[" + index + "].command",
                            "Validation command text must not be blank"));
            }
        }

        if (!issues.isEmpty())
            throw new InvalidCaseSchemaException("unknown", caseSource, catalogCase.id(), List.copyOf(issues));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> T readClasspathJson(ObjectMapper objectMapper, String resourcePath, Class<T> type) {
        try (InputStream input = BenchmarkCatalog.class.getResourceAsStream(resourcePath)) {
            if (input == null)
                throw new CatalogLoadingException("Missing benchmark resource: " + resourcePath, "classpath:" + resourcePath);
            return objectMapper.readValue(input, type);
        } catch (IOException e) {
            throw new CatalogLoadingException("Unable to read benchmark resource " + resourcePath, "classpath:" + resourcePath, e);
        }
    }

    private static <T> T readFileJson(ObjectMapper objectMapper, Path file, Class<T> type) {
        try (InputStream input = Files.newInputStream(file)) {
            return objectMapper.readValue(input, type);
        } catch (IOException e) {
            throw new CatalogLoadingException("Unable to read benchmark file " + file, file.toString(), e);
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

    private record LoadedCase(BenchmarkCase catalogCase, String caseSource) {
    }

    private interface CatalogSource {

        String catalogSource();

        CatalogDescriptor readDescriptor(ObjectMapper objectMapper);

        LoadedCase readCase(String caseReference, ObjectMapper objectMapper);
    }

    private static final class ClasspathCatalogSource implements CatalogSource {

        private final String descriptorResource;

        private ClasspathCatalogSource(String descriptorResource) {
            this.descriptorResource = descriptorResource;
        }

        @Override
        public String catalogSource() {
            return "classpath:" + descriptorResource;
        }

        @Override
        public CatalogDescriptor readDescriptor(ObjectMapper objectMapper) {
            return readClasspathJson(objectMapper, descriptorResource, CatalogDescriptor.class);
        }

        @Override
        public LoadedCase readCase(String caseReference, ObjectMapper objectMapper) {
            return new LoadedCase(readClasspathJson(objectMapper, caseReference, BenchmarkCase.class), "classpath:" + caseReference);
        }
    }

    private static final class FileSystemCatalogSource implements CatalogSource {

        private final Path descriptorFile;
        private final Path baseDirectory;

        private FileSystemCatalogSource(Path catalogPath) {
            Path normalizedPath = catalogPath.toAbsolutePath().normalize();
            if (Files.isDirectory(normalizedPath)) {
                descriptorFile = normalizedPath.resolve("catalog.json");
                baseDirectory = normalizedPath;
            } else {
                descriptorFile = normalizedPath;
                baseDirectory = normalizedPath.getParent();
            }
        }

        @Override
        public String catalogSource() {
            return descriptorFile.toString();
        }

        @Override
        public CatalogDescriptor readDescriptor(ObjectMapper objectMapper) {
            if (!Files.isRegularFile(descriptorFile))
                throw new CatalogLoadingException("Catalog descriptor not found: " + descriptorFile, descriptorFile.toString());
            return readFileJson(objectMapper, descriptorFile, CatalogDescriptor.class);
        }

        @Override
        public LoadedCase readCase(String caseReference, ObjectMapper objectMapper) {
            Path caseFile = baseDirectory.resolve(caseReference).normalize();
            return new LoadedCase(readFileJson(objectMapper, caseFile, BenchmarkCase.class), caseFile.toString());
        }
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

    public record ValidationIssue(String fieldPath, String message) {
    }

    public static final class InvalidCaseSchemaException extends RuntimeException {

        private final String catalogSource;
        private final String caseSource;
        private final String caseId;
        private final List<ValidationIssue> issues;

        public InvalidCaseSchemaException(
                String catalogSource,
                String caseSource,
                String caseId,
                List<ValidationIssue> issues) {
            super("Catalog case definition is invalid: " + caseSource);
            this.catalogSource = catalogSource;
            this.caseSource = caseSource;
            this.caseId = caseId;
            this.issues = issues;
        }

        public String catalogSource() {
            return catalogSource;
        }

        public String caseSource() {
            return caseSource;
        }

        public String caseId() {
            return caseId;
        }

        public List<ValidationIssue> issues() {
            return issues;
        }
    }

    public static final class CatalogLoadingException extends RuntimeException {

        private final String catalogSource;

        public CatalogLoadingException(String message, String catalogSource) {
            super(message);
            this.catalogSource = catalogSource;
        }

        public CatalogLoadingException(String message, String catalogSource, Throwable cause) {
            super(message, cause);
            this.catalogSource = catalogSource;
        }

        public String catalogSource() {
            return catalogSource;
        }
    }
}
