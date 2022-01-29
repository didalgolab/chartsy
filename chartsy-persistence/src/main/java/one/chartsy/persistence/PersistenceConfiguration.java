package one.chartsy.persistence;

import liquibase.integration.spring.SpringLiquibase;
import one.chartsy.kernel.config.KernelConfiguration;
import one.chartsy.persistence.domain.services.PersistentSymbolGroupHierarchy;
import one.chartsy.persistence.event.HibernateEntityEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import javax.sql.DataSource;

@Configuration
@ConditionalOnBean(KernelConfiguration.class)
@EnableJpaRepositories(basePackages = "one.chartsy.persistence.domain.model")
@EntityScan(basePackages = "one.chartsy.persistence.domain")
public class PersistenceConfiguration {

    @Bean
    public PersistentSymbolGroupHierarchy symbolGroupHierarchy() {
        return new PersistentSymbolGroupHierarchy();
    }

    @Bean
    @Lazy(false)
    public HibernateEntityEventPublisher hibernateEntityEventPublisher() {
        return new HibernateEntityEventPublisher();
    }

    @Bean
    public SpringLiquibase liquibase(DataSource dataSource) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:one/chartsy/persistence/changelogs/db.changelog-master.yaml");

        return liquibase;
    }

    @Bean
    public void test() {
        throw new RuntimeException("OK");
    }
}
