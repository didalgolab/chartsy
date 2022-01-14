package one.chartsy.core;

import java.util.Objects;

public abstract class AbstractSimpleLauncher implements Launcher {
    private final Class<? extends LaunchableTarget<?>> supportedTargetType;

    protected <T extends LaunchableTarget<?>> AbstractSimpleLauncher(Class<T> supportedTargetType) {
        Objects.requireNonNull(supportedTargetType);
        this.supportedTargetType = supportedTargetType;
    }

    @Override
    public boolean isSupported(Class<?> targetType) {
        return supportedTargetType.isAssignableFrom(targetType);
    }

    @Override
    public boolean isSupported(LaunchConfiguration<?> configuration) {
        var targets = configuration.getTargets();
        for (var target : targets)
            if (!isSupported(target))
                return false;

        return !targets.isEmpty();
    }
}
