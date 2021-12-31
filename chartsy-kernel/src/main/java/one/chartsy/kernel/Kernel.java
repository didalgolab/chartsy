package one.chartsy.kernel;

import one.chartsy.kernel.config.KernelConfiguration;
import org.openide.util.Lookup;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

@SpringBootApplication
public class Kernel {

    private static final String[] EMPTY_ARGS = new String[0];

    private final GenericApplicationContext applicationContext;

    public Kernel() {
        this(Lookup.getDefault().lookup(KernelConfiguration.class), EMPTY_ARGS);
    }

    public Kernel(KernelConfiguration configuration, String[] args) {
        applicationContext = (GenericApplicationContext) configuration.createApplication().run(args);
        applicationContext.registerBean("kernel", Kernel.class, () -> this);
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
