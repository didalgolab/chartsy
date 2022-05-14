/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel.runner;

import one.chartsy.Candle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Disabled("Disabled temporarily due to java.nio.BufferOverflowException on Linux")
public class LauncherTest {

    String target = "example.JavaClass";
    Path projectDir;

    @BeforeEach
    public void setUp() throws Exception {
        projectDir = Path.of(getClass().getResource("/gradle-project-example").toURI());
    }

    @Test
    public void launch_compiles_sources_beforehand() throws URISyntaxException, IOException, ClassNotFoundException, LaunchException {
        var compiledFile = "/gradle-project-example/build/classes/java/main/example/JavaClass.class";
        var compiledFileURL = getResource(compiledFile);
        if (compiledFileURL != null)
            Files.deleteIfExists(Path.of(compiledFileURL.toURI()));

        assertThat( getResource(compiledFile) ).isNull();
        new EmbeddedLauncher((projectDir, target) -> {}).launch(projectDir, target);
        assertThat( getResource(compiledFile) ).isNotNull();
    }

    //@Test
    public void launch_invokes_launch_performer_asserting(LaunchPerformer performer) throws URISyntaxException, IOException, ClassNotFoundException, LaunchException {
        var compiledFile = "/gradle-project-example/build/classes/java/main/example/JavaClass.class";
        var compiledFileURL = getResource(compiledFile);
        if (compiledFileURL != null)
            Files.deleteIfExists(Path.of(compiledFileURL.toURI()));

        new EmbeddedLauncher(performer).launch(projectDir, target);
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
