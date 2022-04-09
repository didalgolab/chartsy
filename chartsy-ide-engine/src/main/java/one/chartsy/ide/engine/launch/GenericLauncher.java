package one.chartsy.ide.engine.launch;

import lombok.AllArgsConstructor;
import one.chartsy.kernel.Kernel;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ResultHandler;
import org.openide.LifecycleManager;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
public class GenericLauncher implements Launcher {

    private final LaunchPerformer performer;
    private final long compileJavaTimeout;
    private final TimeUnit compileJavaTimeoutUnit;

    public GenericLauncher(LaunchPerformer performer) {
        this.performer = performer;
        this.compileJavaTimeout = 0;
        this.compileJavaTimeoutUnit = null;
    }

    public GenericLauncher withCompileJavaTimeout(long timeout, TimeUnit unit) {
        return new GenericLauncher(performer, timeout, unit);
    }

    @Override
    public void launch(Path projectDir, String className) throws ClassNotFoundException, LaunchException {
        onBeforeLaunch(projectDir);
        performLaunch(projectDir, loadClass(projectDir, className));
        onAfterLaunch();
    }

    protected void onBeforeLaunch(Path projectDir) {
        LifecycleManager.getDefault().saveAll();
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
        if (compileJavaTimeoutUnit == null)
            return result;
        else
            return result.orTimeout(compileJavaTimeout, compileJavaTimeoutUnit);
    }

    protected void performLaunch(Path projectDir, Class<?> target) throws LaunchException {
        try {
            performer.performLaunch(projectDir, target);
        } catch (LaunchException e) {
            throw e;
        } catch (Exception e) {
            throw new LaunchException("Launch " + target.getSimpleName() + " failed", e);
        }
    }
}
