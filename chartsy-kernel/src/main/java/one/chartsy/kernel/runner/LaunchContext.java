package one.chartsy.kernel.runner;

import one.chartsy.Attributable;
import one.chartsy.kernel.ProgressHandle;
import org.immutables.value.Value;

import java.nio.file.Path;

@Value.Immutable
public interface LaunchContext extends Attributable {

    ProgressHandle progressHandle();

    Path projectDirectory();

    Launcher launcher();
}
