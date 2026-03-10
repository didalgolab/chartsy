package one.chartsy.study.processor;

import one.chartsy.study.StudyDescriptor;
import one.chartsy.study.StudyDescriptorProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ChartStudyProcessorTest {
    private static final String PROVIDER_SERVICE = "META-INF/services/one.chartsy.study.StudyDescriptorProvider";

    @TempDir
    Path tempDir;

    @Test
    void incremental_compile_preserves_descriptors_for_unchanged_studies() throws Exception {
        Path sourcesDir = tempDir.resolve("sources");
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);

        Path alphaStudy = writeStudySource(sourcesDir, "AlphaStudy", "Alpha v1");
        Path betaStudy = writeStudySource(sourcesDir, "BetaStudy", "Beta v1");

        compile(classesDir, alphaStudy, betaStudy);
        assertThat(loadDescriptors(classesDir))
                .extracting(StudyDescriptor::name)
                .containsExactlyInAnyOrder("Alpha v1", "Beta v1");

        writeStudySource(sourcesDir, "AlphaStudy", "Alpha v2");
        compile(classesDir, alphaStudy);

        assertThat(loadDescriptors(classesDir))
                .extracting(StudyDescriptor::name)
                .containsExactlyInAnyOrder("Alpha v2", "Beta v1");
    }

    private Path writeStudySource(Path sourcesDir, String typeName, String studyName) throws IOException {
        Path sourceFile = sourcesDir.resolve("sample").resolve(typeName + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, """
                package sample;

                import one.chartsy.study.ChartStudy;
                import one.chartsy.study.StudyFactory;
                import one.chartsy.study.StudyInputKind;
                import one.chartsy.study.StudyKind;
                import one.chartsy.study.StudyOutput;
                import one.chartsy.study.StudyPlacement;

                @ChartStudy(
                        name = "%s",
                        label = "%s",
                        category = "Tests",
                        kind = StudyKind.INDICATOR,
                        placement = StudyPlacement.OWN_PANEL
                )
                public class %s {
                    @StudyFactory(input = StudyInputKind.CANDLES)
                    public %s() {
                    }

                    @StudyOutput(id = "value")
                    public double value() {
                        return 0.0;
                    }
                }
                """.formatted(studyName, studyName, typeName, typeName), StandardCharsets.UTF_8);
        return sourceFile;
    }

    private void compile(Path classesDir, Path... sourceFiles) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();

        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjects(Arrays.stream(sourceFiles).map(Path::toFile).toArray(File[]::new));

            String classPath = System.getProperty("java.class.path") + File.pathSeparator + classesDir;
            List<String> options = List.of(
                    "--release", "25",
                    "-classpath", classPath,
                    "-d", classesDir.toString()
            );

            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
            task.setProcessors(List.of(new ChartStudyProcessor()));

            boolean success = task.call();
            assertThat(success)
                    .withFailMessage("Compilation failed:%n%s", formatDiagnostics(diagnostics))
                    .isTrue();
        }
    }

    private List<StudyDescriptor> loadDescriptors(Path classesDir) throws Exception {
        Path serviceFile = classesDir.resolve(PROVIDER_SERVICE);
        assertThat(serviceFile).exists();

        try (var classLoader = new URLClassLoader(
                new java.net.URL[] { classesDir.toUri().toURL() },
                ChartStudyProcessorTest.class.getClassLoader())) {
            return Files.readAllLines(serviceFile).stream()
                    .map(String::strip)
                    .filter(name -> !name.isEmpty())
                    .map(name -> instantiateProvider(name, classLoader))
                    .flatMap(provider -> provider.getStudyDescriptors().stream())
                    .toList();
        }
    }

    private StudyDescriptorProvider instantiateProvider(String className, ClassLoader classLoader) {
        try {
            return (StudyDescriptorProvider) Class.forName(className, true, classLoader).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Failed to load provider " + className, ex);
        }
    }

    private String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        return diagnostics.getDiagnostics().stream()
                .map(this::formatDiagnostic)
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("<no diagnostics>");
    }

    private String formatDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        String source = diagnostic.getSource() == null ? "<unknown>" : diagnostic.getSource().getName();
        return diagnostic.getKind() + " " + source + ":" + diagnostic.getLineNumber() + " " + diagnostic.getMessage(Locale.ROOT);
    }
}
