package one.chartsy.ide.engine.launch;

import java.nio.file.Path;

@FunctionalInterface
public interface LaunchPerformer {

    void performLaunch(Path projectDir, Class<?> target) throws Exception;
}
