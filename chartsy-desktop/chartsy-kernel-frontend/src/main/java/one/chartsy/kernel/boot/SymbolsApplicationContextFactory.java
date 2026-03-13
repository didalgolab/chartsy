/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel.boot;

import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import one.chartsy.Workspace;
import one.chartsy.kernel.DataProviderRegistry;
import one.chartsy.kernel.StartupMetrics;
import one.chartsy.kernel.SymbolGroupHierarchy;
import one.chartsy.persistence.domain.model.JdbcSymbolGroupRepository;
import one.chartsy.persistence.domain.model.SymbolGroupRepository;
import one.chartsy.persistence.domain.services.PersistentSymbolGroupHierarchy;
import org.h2.jdbcx.JdbcDataSource;
import org.openide.util.Lookup;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.sql.SQLException;

public final class SymbolsApplicationContextFactory {

    private SymbolsApplicationContextFactory() {
    }

    public static ConfigurableApplicationContext createApplicationContext() {
        StartupMetrics.mark("symbolsContext:start");

        var dataSource = createDataSource();
        StartupMetrics.mark("symbolsContext:dataSourceReady");
        initializeLiquibase(dataSource);
        StartupMetrics.mark("symbolsContext:liquibaseReady");

        var context = new GenericApplicationContext();
        var classLoader = Lookup.getDefault().lookup(ClassLoader.class);
        if (classLoader != null)
            context.setClassLoader(classLoader);

        context.registerBean(ExpressionParser.class, () -> new SpelExpressionParser());
        context.registerBean(DataProviderRegistry.class,
                () -> new DataProviderRegistry(context.getBean(ExpressionParser.class)));
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(SymbolGroupRepository.class,
                () -> new JdbcSymbolGroupRepository(dataSource, context));
        context.registerBean(SymbolGroupHierarchy.class,
                () -> new PersistentSymbolGroupHierarchy(context.getBean(SymbolGroupRepository.class)));
        context.refresh();
        StartupMetrics.mark("symbolsContext:refreshReady");

        StartupMetrics.mark("symbolsContext:ready");
        return context;
    }

    private static DataSource createDataSource() {
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

    private static void initializeLiquibase(DataSource dataSource) {
        if (isSymbolsSchemaReady(dataSource))
            return;

        var liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-symbols.yaml");
        try {
            liquibase.afterPropertiesSet();
        } catch (LiquibaseException e) {
            throw new IllegalStateException("Cannot initialize symbols schema", e);
        }
    }

    private static boolean isSymbolsSchemaReady(DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            return exists(connection, """
                    SELECT 1
                    FROM INFORMATION_SCHEMA.TABLES
                    WHERE TABLE_SCHEMA = 'PUBLIC'
                      AND TABLE_NAME = 'ONE_SYMBOL_GROUPS'
                    """)
                    && exists(connection, """
                    SELECT 1
                    FROM INFORMATION_SCHEMA.SEQUENCES
                    WHERE SEQUENCE_SCHEMA = 'PUBLIC'
                      AND SEQUENCE_NAME = 'ONE_SYMBOL_GROUP_IDS'
                    """);
        } catch (SQLException e) {
            return false;
        }
    }

    private static boolean exists(java.sql.Connection connection, String sql) throws SQLException {
        try (var statement = connection.prepareStatement(sql);
             var resultSet = statement.executeQuery()) {
            return resultSet.next();
        }
    }
}
