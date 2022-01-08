package one.chartsy.kernel.config;

import one.chartsy.Workspace;
import one.chartsy.kernel.DataProviderRegistry;
import org.openide.util.lookup.ServiceProvider;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableAutoConfiguration
@ServiceProvider(service = KernelConfiguration.class)
public class KernelConfiguration {

    @Bean
    public ExpressionParser expressionParser() {
        return new SpelExpressionParser();
    }

    @Bean
    public DataProviderRegistry dataProviderRegistry(ExpressionParser parser) {
        return new DataProviderRegistry(parser);
    }

    @Bean
    public ThreadPoolTaskScheduler threadPoolTaskScheduler() {
        var taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("TaskScheduler");
        return taskScheduler;
    }

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
