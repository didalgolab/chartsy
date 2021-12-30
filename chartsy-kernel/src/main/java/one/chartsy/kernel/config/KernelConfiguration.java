package one.chartsy.kernel.config;

import one.chartsy.Workspace;
import org.openide.util.lookup.ServiceProvider;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableAutoConfiguration
@ServiceProvider(service = KernelConfiguration.class)
public class KernelConfiguration {

    public SpringApplication createApplication() {
        SpringApplication app = new SpringApplication(getClass());
        app.setLazyInitialization(true);
        app.setBannerMode(Banner.Mode.OFF);
        app.setAllowBeanDefinitionOverriding(true);
        app.setDefaultProperties(createDefaultProperties());
        //app.setResourceLoader(new DefaultResourceLoader(Lookup.getDefault().lookup(ClassLoader.class)));
        return app;
    }

    protected Map<String, Object> createDefaultProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("workspace.path", Workspace.current().path().toAbsolutePath().toString());
        return props;
    }
}
