package one.chartsy.kernel;

import one.chartsy.kernel.config.KernelConfiguration;
import org.openide.util.Lookup;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

@SpringBootApplication
public class Kernel {

    private static final String[] EMPTY_ARGS = new String[0];

    private final SpringApplicationBuilder current;

    public Kernel() {
        this(Lookup.getDefault().lookup(KernelConfiguration.class), EMPTY_ARGS);
    }

    public Kernel(KernelConfiguration configuration, String[] args) {
        current = configuration.createSpringApplicationBuilder();
        ((GenericApplicationContext) current.run(args))
                .registerBean("kernel", Kernel.class, () -> this);
    }

    public static Kernel getDefault() {
        return Lookup.getDefault().lookup(Kernel.class);
    }

    public ApplicationContext getApplicationContext() {
        return current.context();
    }

    public void publishEvent(Object event) {
        getApplicationContext().publishEvent(event);
    }
}
