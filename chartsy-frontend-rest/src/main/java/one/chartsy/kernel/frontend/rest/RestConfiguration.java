package one.chartsy.kernel.frontend.rest;

import one.chartsy.kernel.frontend.rest.controllers.SystemController;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackageClasses = SystemController.class)
public class RestConfiguration {
}
