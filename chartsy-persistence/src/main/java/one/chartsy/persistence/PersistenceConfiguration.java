package one.chartsy.persistence;

import one.chartsy.persistence.domain.services.PersistentSymbolGroupHierarchy;
import one.chartsy.persistence.event.HibernateEntityEventPublisher;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
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
    public void test() {
        throw new RuntimeException("OK");
    }
}
