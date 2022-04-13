package one.chartsy.kernel.runner;

import java.nio.file.Path;

public interface Launcher {

    void launch(Path projectDir, String className) throws ClassNotFoundException, LaunchException;
}
