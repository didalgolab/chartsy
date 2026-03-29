package one.chartsy.bench.cli;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import one.chartsy.bench.catalog.BenchmarkCatalog;
import one.chartsy.bench.workspace.GitSnapshotWorkspaceMaterializer;

public class ChartsyBenchCli {

    private static final List<String> supportedCommands = List.of("catalog", "provide", "grade", "run");
    private static final List<String> upstreamDependencies = List.of(
            "org.springaicommunity:bench-core:0.2.0-SNAPSHOT",
            "org.springaicommunity:bench-app:0.2.0-SNAPSHOT");
    private static final String upstreamCliMainClass = "org.springaicommunity.bench.core.cli.BenchMain";

    private final PrintStream out;
    private final PrintStream err;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BenchmarkCatalog defaultCatalog = BenchmarkCatalog.loadDefault(objectMapper);
    private final GitSnapshotWorkspaceMaterializer workspaceMaterializer = new GitSnapshotWorkspaceMaterializer();

    public ChartsyBenchCli(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    public int run(String... args) {
        if (args.length == 0) {
            printUsage(err);
            return 1;
        }
        if (isHelpRequest(args)) {
            printUsage(out);
            return 0;
        }

        return switch (args[0]) {
            case "catalog" -> handleCatalogCommand(tail(args));
            case "provide" -> handleProvideCommand(tail(args));
            case "grade", "run" -> notYetImplemented(args[0], wantsJson(args));
            default -> unknownCommand(args[0], wantsJson(args));
        };
    }

    private int handleCatalogCommand(String[] args) {
        if (args.length == 0) {
            printUsage(err);
            return 1;
        }

        return switch (args[0]) {
            case "list" -> catalogList(optionValue(args, "--format").orElse("text"));
            case "show" -> catalogShow(args);
            case "contracts" -> notYetImplemented("catalog " + args[0], wantsJson(args));
            default -> unknownCommand("catalog " + args[0], wantsJson(args));
        };
    }

    private int catalogList(String format) {
        if ("json".equalsIgnoreCase(format)) {
            out.println(toJson(catalogListPayload()));
            return 0;
        }

        out.println("chartsy-refactor-bench default catalog");
        out.println("Purpose: " + defaultCatalog.purpose());
        out.println("Cases: " + defaultCatalog.listEntries().size());
        for (BenchmarkCatalog.CaseListEntry caseEntry : defaultCatalog.listEntries()) {
            out.println("- " + caseEntry.id() + " :: " + caseEntry.title() + " [" + caseEntry.lineage().snapshotCommitAbbrev() + "]");
        }
        return 0;
    }

    private Map<String, Object> catalogListPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "ok");
        payload.put("catalogId", defaultCatalog.catalogId());
        payload.put("catalogVersion", defaultCatalog.catalogVersion());
        payload.put("purpose", defaultCatalog.purpose());
        payload.put("seedOnly", defaultCatalog.seedOnly());
        payload.put("balancedV1Ready", defaultCatalog.balancedV1Ready());
        payload.put("description", defaultCatalog.description());
        payload.put("catalogSource", defaultCatalog.catalogSource());
        payload.put("cases", defaultCatalog.listEntries());
        payload.put("supportedCommands", supportedCommands);
        payload.put("upstreamDependencies", upstreamDependencies);
        payload.put("upstreamCliMainClass", upstreamCliMainClass);
        return payload;
    }

    private int catalogShow(String[] args) {
        String caseId = optionValue(args, "--case").orElse(null);
        boolean json = wantsJson(args);
        if (caseId == null || caseId.isBlank()) {
            return missingRequiredOption("catalog show", "--case", json);
        }

        BenchmarkCatalog.BenchmarkCase benchmarkCase = defaultCatalog.findCase(caseId);
        if (benchmarkCase == null) {
            return missingCase("catalog show", caseId, defaultCatalog.catalogSource(), json);
        }

        if (json) {
            out.println(toJson(catalogShowPayload(benchmarkCase)));
        } else {
            out.println(benchmarkCase.id() + " :: " + benchmarkCase.title());
            out.println("Snapshot: " + benchmarkCase.lineage().snapshotCommit());
            out.println("Source: " + benchmarkCase.lineage().sourcePath());
        }
        return 0;
    }

    private Map<String, Object> catalogShowPayload(BenchmarkCatalog.BenchmarkCase benchmarkCase) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "ok");
        payload.put("catalogId", defaultCatalog.catalogId());
        payload.put("catalogVersion", defaultCatalog.catalogVersion());
        payload.put("catalogSource", defaultCatalog.catalogSource());
        payload.put("case", benchmarkCase);
        return payload;
    }

    private int handleProvideCommand(String[] args) {
        boolean json = wantsJson(args);
        String caseId = optionValue(args, "--case").orElse(null);
        if (caseId == null || caseId.isBlank())
            return missingRequiredOption("provide", "--case", json);

        String modeValue = optionValue(args, "--mode").orElse(null);
        if (modeValue == null || modeValue.isBlank())
            return missingRequiredOption("provide", "--mode", json);

        String workspaceValue = optionValue(args, "--workspace").orElse(null);
        if (workspaceValue == null || workspaceValue.isBlank())
            return missingRequiredOption("provide", "--workspace", json);

        ProvideMode mode = ProvideMode.fromCliValue(modeValue);
        if (mode == null)
            return unsupportedMode(modeValue, json);

        try {
            BenchmarkCatalog catalog = loadCatalog(optionValue(args, "--catalog").orElse(null));
            BenchmarkCatalog.BenchmarkCase benchmarkCase = catalog.findCase(caseId);
            if (benchmarkCase == null)
                return missingCase("provide", caseId, catalog.catalogSource(), json);

            Path workspace = Path.of(workspaceValue).toAbsolutePath().normalize();
            GitSnapshotWorkspaceMaterializer.MaterializationResult result =
                    workspaceMaterializer.materialize(benchmarkCase, workspace);

            if (json) {
                out.println(toJson(providePayload(catalog, benchmarkCase, mode, result)));
            } else {
                out.println("Prepared case " + benchmarkCase.id() + " in " + mode.cliValue + " mode");
                out.println("Workspace: " + result.workspace());
                out.println("Snapshot: " + benchmarkCase.lineage().snapshotCommit());
            }
            return 0;
        } catch (BenchmarkCatalog.InvalidCaseSchemaException e) {
            return invalidCaseSchema(e, json);
        } catch (BenchmarkCatalog.CatalogLoadingException e) {
            return catalogLoadFailure(e, json);
        } catch (IllegalStateException e) {
            return provideFailure("PROVIDE_FAILED", e.getMessage(), json);
        }
    }

    private BenchmarkCatalog loadCatalog(String catalogPath) {
        if (catalogPath == null || catalogPath.isBlank())
            return defaultCatalog;
        return BenchmarkCatalog.load(Path.of(catalogPath), objectMapper);
    }

    private Map<String, Object> providePayload(
            BenchmarkCatalog catalog,
            BenchmarkCatalog.BenchmarkCase benchmarkCase,
            ProvideMode mode,
            GitSnapshotWorkspaceMaterializer.MaterializationResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "ok");
        payload.put("command", "provide");
        payload.put("catalogId", catalog.catalogId());
        payload.put("catalogVersion", catalog.catalogVersion());
        payload.put("catalogSource", catalog.catalogSource());
        payload.put("caseId", benchmarkCase.id());
        payload.put("workspace", result.workspace().toString());
        payload.put("mode", mode.cliValue);
        payload.put("snapshotCommit", benchmarkCase.lineage().snapshotCommit());
        payload.put("snapshotObjectId", benchmarkCase.lineage().snapshotObjectId());
        payload.put("sourcePath", benchmarkCase.lineage().sourcePath());
        payload.put("editScopeCount", benchmarkCase.editScope().size());
        payload.put("validationCommandCount", benchmarkCase.validationCommands().size());
        payload.put("preparedFiles", result.preparedFiles());
        return payload;
    }

    private int notYetImplemented(String command, boolean json) {
        String message = "The '" + command + "' command is scaffolded but not implemented yet.";
        if (json) {
            err.println(toJson(errorPayload("NOT_YET_IMPLEMENTED", command, message)));
        } else {
            err.println(message);
        }
        return 2;
    }

    private int unknownCommand(String command, boolean json) {
        String message = "Unknown command: " + command;
        if (json) {
            err.println(toJson(errorPayload("UNKNOWN_COMMAND", command, message)));
        } else {
            err.println(message);
            printUsage(err);
        }
        return 1;
    }

    private int missingRequiredOption(String command, String option, boolean json) {
        String message = "Missing required option " + option + " for " + command;
        if (json) {
            Map<String, Object> payload = errorPayload("MISSING_REQUIRED_OPTION", command, message);
            payload.put("option", option);
            err.println(toJson(payload));
        } else {
            err.println(message);
        }
        return 1;
    }

    private int missingCase(String command, String caseId, String catalogSource, boolean json) {
        String message = "Unknown case ID '" + caseId + "' in " + catalogSource;
        if (json) {
            Map<String, Object> payload = errorPayload("MISSING_CASE", command, message);
            payload.put("caseId", caseId);
            payload.put("catalogSource", catalogSource);
            err.println(toJson(payload));
        } else {
            err.println(message);
        }
        return 1;
    }

    private int unsupportedMode(String mode, boolean json) {
        String message = "Unsupported mode '" + mode + "'. Supported modes: " + String.join(", ", ProvideMode.supportedValues());
        if (json) {
            Map<String, Object> payload = errorPayload("UNSUPPORTED_MODE", "provide", message);
            payload.put("mode", mode);
            payload.put("supportedModes", ProvideMode.supportedValues());
            err.println(toJson(payload));
        } else {
            err.println(message);
        }
        return 1;
    }

    private int invalidCaseSchema(BenchmarkCatalog.InvalidCaseSchemaException e, boolean json) {
        if (json) {
            Map<String, Object> payload = errorPayload("INVALID_CASE_SCHEMA", "provide", e.getMessage());
            payload.put("catalogSource", e.catalogSource());
            payload.put("caseId", e.caseId());
            payload.put("caseFile", e.caseSource());
            payload.put("issues", e.issues());
            err.println(toJson(payload));
        } else {
            err.println(e.getMessage());
            for (BenchmarkCatalog.ValidationIssue issue : e.issues()) {
                err.println(" - " + issue.fieldPath() + ": " + issue.message());
            }
        }
        return 1;
    }

    private int catalogLoadFailure(BenchmarkCatalog.CatalogLoadingException e, boolean json) {
        String message = e.getMessage();
        if (json) {
            Map<String, Object> payload = errorPayload("CATALOG_LOAD_FAILURE", "provide", message);
            payload.put("catalogSource", e.catalogSource());
            err.println(toJson(payload));
        } else {
            err.println(message);
        }
        return 1;
    }

    private int provideFailure(String errorCode, String message, boolean json) {
        if (json) {
            err.println(toJson(errorPayload(errorCode, "provide", message)));
        } else {
            err.println(message);
        }
        return 1;
    }

    private Map<String, Object> errorPayload(String errorCode, String command, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "error");
        payload.put("errorCode", errorCode);
        payload.put("command", command);
        payload.put("message", message);
        payload.put("supportedCommands", supportedCommands);
        return payload;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize CLI payload", e);
        }
    }

    private static boolean isHelpRequest(String[] args) {
        return args.length == 1 && ("help".equals(args[0]) || "--help".equals(args[0]) || "-h".equals(args[0]));
    }

    private static boolean wantsJson(String[] args) {
        return hasOption(args, "--json") || optionValue(args, "--format").map("json"::equalsIgnoreCase).orElse(false);
    }

    private static boolean hasOption(String[] args, String option) {
        for (String arg : args) {
            if (option.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<String> optionValue(String[] args, String option) {
        for (int i = 0; i < args.length - 1; i++) {
            if (option.equals(args[i])) {
                return Optional.of(args[i + 1]);
            }
        }
        return Optional.empty();
    }

    private static String[] tail(String[] args) {
        String[] tail = new String[args.length - 1];
        System.arraycopy(args, 1, tail, 0, tail.length);
        return tail;
    }

    private static void printUsage(PrintStream stream) {
        stream.println("Usage: chartsy-refactor-bench <command> [options]");
        stream.println();
        stream.println("Commands:");
        stream.println("  catalog list [--format text|json]");
        stream.println("  catalog contracts [--format text|json]");
        stream.println("  catalog show --case <case-id> [--format text|json]");
        stream.println("  provide --case <case-id> --mode <gold|noop|live> --workspace <path> [--json]");
        stream.println("  grade --workspace <path> [--format text|json]");
        stream.println("  run --case <case-id> --mode <gold|noop|live> [--output <path>] [--json]");
    }

    private enum ProvideMode {
        GOLD("gold"),
        NOOP("noop"),
        LIVE("live");

        private static final List<String> supportedValues = List.of("gold", "noop", "live");

        private final String cliValue;

        ProvideMode(String cliValue) {
            this.cliValue = cliValue;
        }

        private static ProvideMode fromCliValue(String cliValue) {
            for (ProvideMode mode : values()) {
                if (mode.cliValue.equalsIgnoreCase(cliValue))
                    return mode;
            }
            return null;
        }

        private static List<String> supportedValues() {
            return supportedValues;
        }
    }
}
