package one.chartsy.kernel.config;

import org.openide.util.lookup.ServiceProvider;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Configuration;

@Configuration
@ServiceProvider(service = KernelConfiguration.class)
public class KernelConfiguration {

    public SpringApplication createApplication() {
        SpringApplication app = new SpringApplication(getClass());
        app.setLazyInitialization(true);
        app.setBannerMode(Banner.Mode.OFF);
        app.setAllowBeanDefinitionOverriding(true);
        return app;
    }
}
