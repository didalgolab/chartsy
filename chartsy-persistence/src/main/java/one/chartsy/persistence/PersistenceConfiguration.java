package one.chartsy.persistence;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = "one.chartsy.persistence.domain")
@EnableJpaRepositories(basePackages = "one.chartsy.persistence.domain.model")
public class PersistenceConfiguration {

    @Bean
    public void test() {
        throw new RuntimeException("OK");
    }
}
