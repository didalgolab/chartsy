package one.chartsy.core;

import org.openide.util.Lookup;

public interface Launcher {

    static <R> R launch(Class<? extends LaunchableTarget<R>> targetType) {
        var installedLaunchers = Lookup.getDefault().lookupAll(Launcher.class);
        LaunchConfiguration<R> configuration = null;
        for (Launcher launcher : installedLaunchers)
            if (launcher.isSupported(targetType)) {
                if (configuration == null)
                    configuration = LaunchConfiguration.of(targetType);
                if (launcher.isSupported(configuration))
                    return launcher.launch(configuration);
            }

        throw new NotFoundException("Unable to find launcher supporting " + targetType.getSimpleName() + " in default Lookup among currently installed launchers " + installedLaunchers);
    }

    boolean isSupported(Class<?> targetType);

    boolean isSupported(LaunchConfiguration<?> configuration);

    <R> R launch(LaunchConfiguration<R> configuration);

    class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }
}
