/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence;

import one.chartsy.Workspace;
import liquibase.integration.spring.SpringLiquibase;
import one.chartsy.kernel.config.KernelConfiguration;
import one.chartsy.persistence.domain.model.JdbcRunnerRepository;
import one.chartsy.persistence.domain.model.JdbcSymbolGroupRepository;
import one.chartsy.persistence.domain.model.RunnerRepository;
import one.chartsy.persistence.domain.model.SymbolGroupRepository;
import one.chartsy.persistence.domain.services.PersistentSymbolGroupHierarchy;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ApplicationEventPublisher;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

@AutoConfiguration
@ConditionalOnBean(KernelConfiguration.class)
public class PersistenceAutoConfiguration {

    @Bean
    public DataSource dataSource() {
        try {
            Files.createDirectories(Workspace.current().path());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create workspace directory", e);
        }

        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:file:" + Workspace.current().path().resolve("application") + ";COMPRESS=true");
        dataSource.setUser("chartsy");
        dataSource.setPassword("chartsy");
        return dataSource;
    }

    @Bean
    public SymbolGroupRepository symbolGroupRepository(DataSource dataSource, ApplicationEventPublisher eventPublisher, SpringLiquibase liquibase) {
        return new JdbcSymbolGroupRepository(dataSource, eventPublisher);
    }

    @Bean
    public RunnerRepository runnerRepository(DataSource dataSource, SpringLiquibase liquibase) {
        return new JdbcRunnerRepository(dataSource);
    }

    @Bean
    public PersistentSymbolGroupHierarchy symbolGroupHierarchy(SymbolGroupRepository repository, SpringLiquibase liquibase) {
        return new PersistentSymbolGroupHierarchy(repository);
    }

    @Bean
    public SpringLiquibase liquibase(DataSource dataSource) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");

        return liquibase;
    }
}
