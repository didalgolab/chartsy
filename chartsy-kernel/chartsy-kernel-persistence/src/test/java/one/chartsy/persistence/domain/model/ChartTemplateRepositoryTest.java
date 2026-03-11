/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence.domain.model;

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import one.chartsy.persistence.domain.ChartTemplateAggregateData;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChartTemplateRepositoryTest {

    @TempDir
    Path workspace;

    @Test
    void liquibaseSchemaAndHibernateMappingPersistChartTemplatesInH2() throws Exception {
        String jdbcUrl = jdbcUrl("chart-template-schema");
        applyChangelog(jdbcUrl);

        StandardServiceRegistry registry = createRegistry(jdbcUrl);
        try {
            MetadataSources metadataSources = new MetadataSources(registry)
                    .addAnnotatedClass(ChartTemplateAggregateData.class);

            try (SessionFactory sessionFactory = metadataSources.buildMetadata().buildSessionFactory();
                 var session = sessionFactory.openSession()) {
                var transaction = session.beginTransaction();
                ChartTemplateAggregateData template = new ChartTemplateAggregateData();
                template.setTemplateKey(UUID.randomUUID());
                template.setName("My Template");
                template.setNameKey("my template");
                template.setOrigin(ChartTemplateAggregateData.Origin.USER);
                template.setDefaultTemplate(true);
                template.setPayloadVersion(1);
                template.setPayloadJson("{\"overlays\":[],\"indicators\":[]}");
                session.persist(template);
                transaction.commit();

                assertThat(session.createQuery("from ChartTemplateAggregateData", ChartTemplateAggregateData.class).list())
                        .extracting(ChartTemplateAggregateData::getName)
                        .containsExactly("My Template");
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    void schemaEnforcesNameKeyUniqueness() throws Exception {
        String jdbcUrl = jdbcUrl("chart-template-unique");
        applyChangelog(jdbcUrl);

        StandardServiceRegistry registry = createRegistry(jdbcUrl);
        try {
            MetadataSources metadataSources = new MetadataSources(registry)
                    .addAnnotatedClass(ChartTemplateAggregateData.class);

            try (SessionFactory sessionFactory = metadataSources.buildMetadata().buildSessionFactory();
                 var session = sessionFactory.openSession()) {
                var transaction = session.beginTransaction();
                session.persist(newTemplate("Alpha", "alpha"));
                session.persist(newTemplate("ALPHA", "alpha"));

                assertThatThrownBy(session::flush).isInstanceOf(Exception.class);
                transaction.rollback();
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private String jdbcUrl(String databaseName) {
        return "jdbc:h2:file:" + workspace.resolve(databaseName + "-" + UUID.randomUUID()) + ";DB_CLOSE_DELAY=-1";
    }

    private StandardServiceRegistry createRegistry(String jdbcUrl) {
        return new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.JAKARTA_JDBC_URL, jdbcUrl)
                .applySetting(AvailableSettings.JAKARTA_JDBC_USER, "sa")
                .applySetting(AvailableSettings.JAKARTA_JDBC_PASSWORD, "")
                .applySetting(AvailableSettings.JAKARTA_JDBC_DRIVER, "org.h2.Driver")
                .applySetting(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .applySetting(AvailableSettings.PHYSICAL_NAMING_STRATEGY, CamelCaseToUnderscoresNamingStrategy.class.getName())
                .build();
    }

    private ChartTemplateAggregateData newTemplate(String name, String nameKey) {
        ChartTemplateAggregateData template = new ChartTemplateAggregateData();
        template.setTemplateKey(UUID.randomUUID());
        template.setName(name);
        template.setNameKey(nameKey);
        template.setOrigin(ChartTemplateAggregateData.Origin.USER);
        template.setDefaultTemplate(false);
        template.setPayloadVersion(1);
        template.setPayloadJson("{\"overlays\":[],\"indicators\":[]}");
        return template;
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
