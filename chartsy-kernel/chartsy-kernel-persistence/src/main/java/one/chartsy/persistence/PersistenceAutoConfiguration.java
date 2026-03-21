/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence;

import one.chartsy.Workspace;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import one.chartsy.kernel.config.KernelConfiguration;
import one.chartsy.SymbolGroupContent;
import one.chartsy.persistence.domain.RunnerAggregateData;
import one.chartsy.persistence.domain.SymbolGroupAggregateData;
import one.chartsy.persistence.domain.model.GeneratedRunnerRepository;
import one.chartsy.persistence.domain.model.GeneratedSymbolGroupRepository;
import one.chartsy.persistence.domain.model.RunnerRepository;
import one.chartsy.persistence.domain.model.SpringGeneratedRunnerRepository;
import one.chartsy.persistence.domain.model.SpringGeneratedSymbolGroupRepository;
import one.chartsy.persistence.domain.model.SymbolGroupRepository;
import one.chartsy.persistence.domain.services.PersistentSymbolGroupHierarchy;
import one.chartsy.kernel.StartupMetrics;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.convert.DataAccessStrategy;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.jdbc.repository.QueryMappingConfiguration;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.support.JdbcRepositoryFactory;
import org.springframework.data.mapping.callback.EntityCallbacks;
import org.springframework.data.relational.core.dialect.Dialect;
import org.springframework.data.relational.core.dialect.H2Dialect;
import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;
import org.springframework.core.convert.converter.Converter;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

@AutoConfiguration
@ConditionalOnBean(KernelConfiguration.class)
public class PersistenceAutoConfiguration extends AbstractJdbcConfiguration {

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
    public NamedParameterJdbcOperations namedParameterJdbcOperations(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    public GeneratedSymbolGroupRepository generatedSymbolGroupRepository(
            DataAccessStrategy dataAccessStrategy,
            JdbcMappingContext mappingContext,
            JdbcConverter jdbcConverter,
            NamedParameterJdbcOperations jdbcOperations,
            ApplicationEventPublisher eventPublisher,
            BeanFactory beanFactory
    ) {
        return createRepository(
                GeneratedSymbolGroupRepository.class,
                dataAccessStrategy,
                mappingContext,
                jdbcConverter,
                jdbcOperations,
                eventPublisher,
                beanFactory
        );
    }

    @Bean
    @Lazy
    public GeneratedRunnerRepository generatedRunnerRepository(
            DataAccessStrategy dataAccessStrategy,
            JdbcMappingContext mappingContext,
            JdbcConverter jdbcConverter,
            NamedParameterJdbcOperations jdbcOperations,
            ApplicationEventPublisher eventPublisher,
            BeanFactory beanFactory
    ) {
        return createRepository(
                GeneratedRunnerRepository.class,
                dataAccessStrategy,
                mappingContext,
                jdbcConverter,
                jdbcOperations,
                eventPublisher,
                beanFactory
        );
    }

    @Bean
    public SymbolGroupRepository symbolGroupRepository(
            GeneratedSymbolGroupRepository delegate,
            ApplicationEventPublisher eventPublisher,
            SpringLiquibase liquibase
    ) {
        return new SpringGeneratedSymbolGroupRepository(delegate, eventPublisher);
    }

    @Bean
    @Lazy
    public RunnerRepository runnerRepository(GeneratedRunnerRepository delegate, SpringLiquibase liquibase) {
        return new SpringGeneratedRunnerRepository(delegate);
    }

    @Bean
    public PersistentSymbolGroupHierarchy symbolGroupHierarchy(SymbolGroupRepository repository, SpringLiquibase liquibase) {
        return new PersistentSymbolGroupHierarchy(repository);
    }

    @Bean
    public SpringLiquibase liquibase(DataSource dataSource) {
        SpringLiquibase liquibase = new MeasuredSpringLiquibase("kernelContext:liquibase");
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");

        return liquibase;
    }

    @Override
    @Bean
    public JdbcCustomConversions jdbcCustomConversions() {
        return new JdbcCustomConversions(List.of(
                new SymbolGroupContentTypeReadingConverter(),
                new SymbolGroupContentTypeWritingConverter()
        ));
    }

    @Override
    @Bean
    public Dialect jdbcDialect(NamedParameterJdbcOperations operations) {
        return H2Dialect.INSTANCE;
    }

    @Bean
    public BeforeConvertCallback<SymbolGroupAggregateData> symbolGroupAggregateBeforeConvert(DataSource dataSource) {
        return aggregate -> {
            if (aggregate.getId() == null) {
                aggregate.setId(nextId(dataSource, "ONE_SYMBOL_GROUP_IDS"));
                aggregate.markNewlyCreated();
            } else {
                aggregate.markModified();
            }
            return aggregate;
        };
    }

    @Bean
    public BeforeConvertCallback<RunnerAggregateData> runnerAggregateBeforeConvert(DataSource dataSource) {
        return aggregate -> {
            if (aggregate.getId() == null) {
                aggregate.setId(nextId(dataSource, "ONE_RUNNER_IDS"));
                aggregate.markNewlyCreated();
            } else {
                aggregate.markModified();
            }
            return aggregate;
        };
    }

    @Override
    protected Set<Class<?>> getInitialEntitySet() {
        return Set.of(
                SymbolGroupAggregateData.class,
                RunnerAggregateData.class
        );
    }

    private <T> T createRepository(
            Class<T> repositoryType,
            DataAccessStrategy dataAccessStrategy,
            JdbcMappingContext mappingContext,
            JdbcConverter jdbcConverter,
            NamedParameterJdbcOperations jdbcOperations,
            ApplicationEventPublisher eventPublisher,
            BeanFactory beanFactory
    ) {
        var factory = new JdbcRepositoryFactory(
                dataAccessStrategy,
                mappingContext,
                jdbcConverter,
                H2Dialect.INSTANCE,
                eventPublisher,
                jdbcOperations
        );
        factory.setBeanFactory(beanFactory);
        factory.setEntityCallbacks(EntityCallbacks.create(beanFactory));
        factory.setQueryMappingConfiguration(QueryMappingConfiguration.EMPTY);
        return factory.getRepository(repositoryType);
    }

    private static long nextId(DataSource dataSource, String sequenceName) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT NEXT VALUE FOR " + sequenceName);
             var resultSet = statement.executeQuery()) {
            if (!resultSet.next())
                throw new IllegalStateException("Sequence " + sequenceName + " did not return a value");
            return resultSet.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot obtain next value from " + sequenceName, e);
        }
    }

    @ReadingConverter
    static final class SymbolGroupContentTypeReadingConverter implements Converter<String, SymbolGroupContent.Type> {

        @Override
        public SymbolGroupContent.Type convert(String value) {
            try {
                int ordinal = Integer.parseInt(value);
                return SymbolGroupContent.Type.values()[ordinal];
            } catch (NumberFormatException ignored) {
                return SymbolGroupContent.Type.valueOf(value);
            }
        }
    }

    @WritingConverter
    static final class SymbolGroupContentTypeWritingConverter implements Converter<SymbolGroupContent.Type, String> {

        @Override
        public String convert(SymbolGroupContent.Type value) {
            return Integer.toString(value.ordinal());
        }
    }

    static final class MeasuredSpringLiquibase extends SpringLiquibase {

        private final String stagePrefix;

        MeasuredSpringLiquibase(String stagePrefix) {
            this.stagePrefix = stagePrefix;
        }

        @Override
        public void afterPropertiesSet() throws LiquibaseException {
            StartupMetrics.mark(stagePrefix + ":start");
            try {
                super.afterPropertiesSet();
            } finally {
                StartupMetrics.mark(stagePrefix + ":ready");
            }
        }
    }
}
