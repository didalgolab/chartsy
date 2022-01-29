package one.chartsy.frontend;

import one.chartsy.kernel.Kernel;
import one.chartsy.frontend.config.FrontEndConfiguration;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

@ServiceProvider(service = FrontEnd.class)
public class FrontEndObject implements FrontEnd {

    private final GenericApplicationContext applicationContext;


    public FrontEndObject() {
        this(Lookup.getDefault().lookup(FrontEndConfiguration.class), null);
    }

    public FrontEndObject(Kernel kernel) {
        this(Lookup.getDefault().lookup(FrontEndConfiguration.class), kernel.getApplicationContext());
    }

    public FrontEndObject(FrontEndConfiguration config, ApplicationContext parent) {
        applicationContext = (GenericApplicationContext) config.createApplication(parent).run();
        applicationContext.registerBean("frontEnd", FrontEnd.class, () -> this);
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public GenericApplicationContext getGenericApplicationContext() {
        return applicationContext;
    }
}
