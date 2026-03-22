/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel.config;

import one.chartsy.Workspace;
import one.chartsy.kernel.DataProviderRegistry;
import one.chartsy.kernel.starter.AbstractSpringApplicationBuilderFactory;
import org.openide.util.lookup.ServiceProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ServiceProvider(service = KernelConfiguration.class)
public class KernelConfiguration extends AbstractSpringApplicationBuilderFactory {

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

    @Override
    protected void registerConfigurationClasses(AnnotationConfigApplicationContext context) {
        super.registerConfigurationClasses(context);
        registerConfigurationClass(context, "one.chartsy.persistence.PersistenceAutoConfiguration");
    }

    @Override
    protected Map<String, Object> createDefaultProperties(String[] args) {
        Map<String, Object> props = new HashMap<>();
        props.put("workspace.path", Workspace.current().path().toAbsolutePath().toString());
        return props;
    }
}
