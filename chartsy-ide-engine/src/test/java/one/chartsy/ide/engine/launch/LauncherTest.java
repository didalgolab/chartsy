package one.chartsy.ide.engine.launch;

import one.chartsy.Candle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openide.LifecycleManager;
import org.openide.util.lookup.Lookups;
import org.opentest4j.AssertionFailedError;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LauncherTest {

    String target = "example.JavaClass";
    Path projectDir;

    @BeforeEach
    public void setUp() throws Exception {
        projectDir = Path.of(getClass().getResource("/gradle-project-example").toURI());
    }

    @Test
    public void launch_saves_all_unsaved_documents_beforehand() {
        // having
        var saveAllCalled = new AtomicBoolean();
        var lcm = new LifecycleManager() {
            @Override public void exit() { }
            @Override public void saveAll() {
                saveAllCalled.set(true);
            }
        };
        // test & assert
        Lookups.executeWith(Lookups.singleton(lcm),
                () -> {
                    try {
                        new GenericLauncher(
                                (Path projectDir, Class<?> target) -> assertThat(saveAllCalled).as("saveAll called?").isTrue())
                                .launch(projectDir, target);
                    } catch (ClassNotFoundException e) {
                        throw new AssertionFailedError("Can't load compiled class", e);
                    } catch (LaunchException e) {
                        throw new AssertionFailedError("Can't launch compiled class", e);
                    }
                });
    }

    @Test
    public void launch_compiles_sources_beforehand() throws URISyntaxException, IOException, ClassNotFoundException, LaunchException {
        var compiledFile = "/gradle-project-example/build/classes/java/main/example/JavaClass.class";
        var compiledFileURL = getResource(compiledFile);
        if (compiledFileURL != null)
            Files.deleteIfExists(Path.of(compiledFileURL.toURI()));

        assertThat( getResource(compiledFile) ).isNull();
        new GenericLauncher((Path projectDir, Class<?> target) -> { }).launch(projectDir, target);
        assertThat( getResource(compiledFile) ).isNotNull();
    }

    //@Test
    public void launch_invokes_launch_performer_asserting(LaunchPerformer performer) throws URISyntaxException, IOException, ClassNotFoundException, LaunchException {
        var compiledFile = "/gradle-project-example/build/classes/java/main/example/JavaClass.class";
        var compiledFileURL = getResource(compiledFile);
        if (compiledFileURL != null)
            Files.deleteIfExists(Path.of(compiledFileURL.toURI()));

        new GenericLauncher(performer).launch(projectDir, target);
    }

    @Test
    public void launch_can_perform_on_compiled_classes() throws Exception {
        launch_invokes_launch_performer_asserting((projectDir, classTarget) -> {
            assertEquals(target, classTarget.getName());

            ClassLoader classLoader = classTarget.getClassLoader();
            assertEquals("example.JavaClass",
                    classLoader.loadClass("example.JavaClass").getName());
            assertEquals(Candle.class,
                    classLoader.loadClass("one.chartsy.Candle"));
            assertThrows(ClassNotFoundException.class, () ->
                    classLoader.loadClass("xyz.NotExistingClass"));
        });
    }

    private URL getResource(String name) {
        return getClass().getResource(name);
    }
}
