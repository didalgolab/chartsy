package one.chartsy.bench.cli;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ChartsyBenchCli {

    private static final List<String> supportedCommands = List.of("catalog", "provide", "grade", "run");
    private static final List<String> upstreamDependencies = List.of(
            "org.springaicommunity:bench-core:0.2.0-SNAPSHOT",
            "org.springaicommunity:bench-app:0.2.0-SNAPSHOT");
    private static final String upstreamCliMainClass = "org.springaicommunity.bench.core.cli.BenchMain";
    private static final String catalogVersion = "0.1.0-SNAPSHOT";

    private final PrintStream out;
    private final PrintStream err;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            case "provide", "grade", "run" -> notYetImplemented(args[0], wantsJson(args));
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
            case "contracts", "show" -> notYetImplemented("catalog " + args[0], wantsJson(args));
            default -> unknownCommand("catalog " + args[0], wantsJson(args));
        };
    }

    private int catalogList(String format) {
        if ("json".equalsIgnoreCase(format)) {
            out.println(toJson(catalogListPayload()));
            return 0;
        }

        out.println("chartsy-refactor-bench scaffold (0 cases)");
        out.println("Upstream CLI: " + upstreamCliMainClass);
        return 0;
    }

    private Map<String, Object> catalogListPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "scaffold");
        payload.put("suiteId", "chartsy-refactor-bench");
        payload.put("catalogVersion", catalogVersion);
        payload.put("cases", List.of());
        payload.put("supportedCommands", supportedCommands);
        payload.put("upstreamDependencies", upstreamDependencies);
        payload.put("upstreamCliMainClass", upstreamCliMainClass);
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
}
