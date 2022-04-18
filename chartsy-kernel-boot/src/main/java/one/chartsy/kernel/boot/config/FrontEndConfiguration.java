package one.chartsy.kernel.boot.config;

import one.chartsy.kernel.starter.AbstractSpringApplicationBuilderFactory;
import org.openide.util.lookup.ServiceProvider;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.*;

@Configuration
@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
@ServiceProvider(service = FrontEndConfiguration.class)
public class FrontEndConfiguration extends AbstractSpringApplicationBuilderFactory {

}
