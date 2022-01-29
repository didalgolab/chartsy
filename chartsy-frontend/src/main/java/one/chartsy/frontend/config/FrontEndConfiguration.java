package one.chartsy.frontend.config;

import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.io.DefaultResourceLoader;

@Configuration
@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
@ServiceProvider(service = FrontEndConfiguration.class)
public class FrontEndConfiguration {

    public SpringApplication createApplication(ApplicationContext parent) {
        SpringApplication app = new SpringApplication(getClass());
        app.setLazyInitialization(true);
        app.setBannerMode(Banner.Mode.OFF);
        app.setAllowBeanDefinitionOverriding(true);
        app.setResourceLoader(new DefaultResourceLoader(Lookup.getDefault().lookup(ClassLoader.class)));

        return app;

//        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
//        context.setResourceLoader(new DefaultResourceLoader(Lookup.getDefault().lookup(ClassLoader.class)));
//        context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor());
//        context.register(getClass());
//        context.refresh();
//        if (parent != null)
//            context.setParent(parent);
//        return context;
    }
}
