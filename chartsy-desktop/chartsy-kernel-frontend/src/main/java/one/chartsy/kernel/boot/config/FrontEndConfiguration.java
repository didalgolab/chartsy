/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel.boot.config;

import one.chartsy.kernel.starter.AbstractSpringApplicationBuilderFactory;
import org.openide.util.lookup.ServiceProvider;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.dao.PersistenceExceptionTranslationAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jdbc.JdbcRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcClientAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.netty.NettyAutoConfiguration;
import org.springframework.boot.autoconfigure.reactor.ReactorAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;

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
@ServiceProvider(service = FrontEndConfiguration.class)
public class FrontEndConfiguration extends AbstractSpringApplicationBuilderFactory {

    @Override
    public SpringApplicationBuilder createSpringApplicationBuilder() {
        return super.createSpringApplicationBuilder()
                .web(WebApplicationType.NONE);
    }
}
