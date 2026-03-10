/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence.domain.model;

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import one.chartsy.persistence.domain.RunnerAggregateData;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RunnerRepositoryTest {

    @TempDir
    Path workspace;

    @Test
    void liquibase_schema_and_hibernate_mapping_load_seed_runner_from_h2() throws Exception {
        String databaseName = "runner-repository-" + UUID.randomUUID();
        String jdbcUrl = "jdbc:h2:file:" + workspace.resolve(databaseName) + ";DB_CLOSE_DELAY=-1";

        applyChangelog(jdbcUrl);

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.JAKARTA_JDBC_URL, jdbcUrl)
                .applySetting(AvailableSettings.JAKARTA_JDBC_USER, "sa")
                .applySetting(AvailableSettings.JAKARTA_JDBC_PASSWORD, "")
                .applySetting(AvailableSettings.JAKARTA_JDBC_DRIVER, "org.h2.Driver")
                .applySetting(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .applySetting(AvailableSettings.PHYSICAL_NAMING_STRATEGY, CamelCaseToUnderscoresNamingStrategy.class.getName())
                .build();
        try {
            MetadataSources metadataSources = new MetadataSources(registry)
                    .addAnnotatedClass(RunnerAggregateData.class);

            try (SessionFactory sessionFactory = metadataSources.buildMetadata().buildSessionFactory();
                 var session = sessionFactory.openSession()) {
                assertThat(session.createQuery("from RunnerAggregateData", RunnerAggregateData.class).list())
                        .extracting(RunnerAggregateData::getKey)
                        .contains("EXPLORATION");
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private void applyChangelog(String jdbcUrl) throws Exception {
        try (var connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            var database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.yaml",
                    new ClassLoaderResourceAccessor(getClass().getClassLoader()),
                    database)) {
                liquibase.update(new Contexts());
            }
        }
    }
}
