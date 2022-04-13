package one.chartsy.kernel.runner;

import org.openide.util.Lookup;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;

public interface LaunchServices {

    List<LaunchPerformer.Descriptor> findCompatibleRunners(Class<?> target);

    LaunchPerformer createRunner(Class<?> target) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException;

    default LaunchPerformer getDefaultRunner() {
        return new LaunchPerformer() {
            @Override
            public void performLaunch(LaunchContext context, Class<?> target) throws Exception {
                LaunchPerformer runner = createRunner(target);
                runner.performLaunch(context, target);
            }
        };
    }

    static LaunchServices getDefault() {
        return Optional.ofNullable(Lookup.getDefault().lookup(LaunchServices.class))
                .orElseThrow(() -> new UnsupportedOperationException("Unable to find LaunchServices implementation"));
    }
}
