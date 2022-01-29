package one.chartsy.frontend.rest;

import one.chartsy.frontend.rest.controllers.SystemController;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;

@Configuration
@ComponentScan(basePackageClasses = SystemController.class)
public class RestConfiguration {
}
