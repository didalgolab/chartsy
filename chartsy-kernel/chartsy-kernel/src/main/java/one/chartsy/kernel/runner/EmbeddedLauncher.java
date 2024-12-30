/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel.runner;

import one.chartsy.kernel.FrontEndInterface;
import one.chartsy.kernel.Kernel;
import one.chartsy.kernel.ProgressHandle;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ResultHandler;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class EmbeddedLauncher implements Launcher {

    private final LaunchPerformer performer;
    private final FrontEndInterface frontEnd;
    private final Map<String,?> attributes;

    public EmbeddedLauncher(LaunchPerformer performer) {
        this(performer, Map.of(), FrontEndInterface.get());
    }

    public EmbeddedLauncher(LaunchPerformer performer, Map<String,?> attributes, FrontEndInterface frontEnd) {
        this.performer = performer;
        this.frontEnd = frontEnd;
        this.attributes = Map.copyOf(attributes);
    }

    public EmbeddedLauncher withFrontEndInterface(FrontEndInterface frontEnd) {
        return new EmbeddedLauncher(performer, attributes, frontEnd);
    }

    public EmbeddedLauncher withInitialAttributes(Map<String,?> attributes) {
        return new EmbeddedLauncher(performer, attributes, frontEnd);
    }

    @Override
    public void launch(Path projectDir, String className) throws ClassNotFoundException, LaunchException {
        ProgressHandle ph = frontEnd.createProgressHandle("Starting " + simpleClassName(className) + "...", null);
        try {
            onBeforeLaunch(projectDir);
            LaunchContext context = createContext(projectDir, ph);
            performLaunch(context, loadClass(projectDir, className));
            onAfterLaunch();
        } finally {
            ph.finish();
        }
    }

    private static String simpleClassName(String className) {
        int dot = className.lastIndexOf('.');
        return (dot < 0)? className: className.substring(dot + 1);
    }

    protected void onBeforeLaunch(Path projectDir) {
        compileJava(projectDir).join();
    }

    protected void onAfterLaunch() {
    }

    private Class<?> loadClass(Path projectDir, String className) throws ClassNotFoundException {
        ClassLoader cl = new ConventionalJavaProjectClassLoader(Kernel.class.getClassLoader(), projectDir);
        return cl.loadClass(className);
    }


    protected CompletableFuture<Void> compileJava(Path projectDir) {
        var result = new CompletableFuture<Void>();
        var connector = GradleConnector.newConnector().forProjectDirectory(projectDir.toFile());

        try (var conn = connector.connect()) {
            var taskRunner = conn.newBuild().forTasks("compileJava");
            taskRunner.run(new ResultHandler<>() {
                @Override
                public void onComplete(Void unused) {
                    result.complete(null);
                }

                @Override
                public void onFailure(GradleConnectionException e) {
                    result.completeExceptionally(e);
                }
            });
        }
        return result;
    }

    protected LaunchContext createContext(Path projectDir, ProgressHandle ph) {
        return ImmutableLaunchContext.builder()
                .progressHandle(ph)
                .projectDirectory(projectDir)
                .attributes(attributes)
                .launcher(this)
                .build();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    protected void performLaunch(LaunchContext context, Class<?> target) throws LaunchException {
        try {
            performer.performLaunch(context, target);
        } catch (LaunchException e) {
            throw e;
        } catch (Exception e) {
            throw new LaunchException("Launch " + target.getSimpleName() + " failed", e);
        } finally {
            Thread.interrupted(); // clear interrupted status of this thread, just in case
        }
    }
}
