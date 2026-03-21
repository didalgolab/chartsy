/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel.config;

import one.chartsy.Workspace;
import one.chartsy.kernel.DataProviderRegistry;
import one.chartsy.kernel.starter.AbstractSpringApplicationBuilderFactory;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.dao.PersistenceExceptionTranslationAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jdbc.JdbcRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcClientAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.netty.NettyAutoConfiguration;
import org.springframework.boot.autoconfigure.reactor.ReactorAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableAutoConfiguration(exclude = {
        AopAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        JdbcClientAutoConfiguration.class,
        JdbcRepositoriesAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class,
        NettyAutoConfiguration.class,
        PersistenceExceptionTranslationAutoConfiguration.class,
        ReactorAutoConfiguration.class,
        TransactionAutoConfiguration.class
})
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
    public SpringApplicationBuilder createSpringApplicationBuilder() {
        return super.createSpringApplicationBuilder()
                .web(WebApplicationType.NONE)
                .resourceLoader(new DefaultResourceLoader(Lookup.getDefault().lookup(ClassLoader.class)))
                .properties(createDefaultProperties());
    }

    protected Map<String, Object> createDefaultProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("workspace.path", Workspace.current().path().toAbsolutePath().toString());
        return props;
    }
}
