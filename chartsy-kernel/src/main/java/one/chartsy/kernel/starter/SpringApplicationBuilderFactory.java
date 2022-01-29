package one.chartsy.kernel.starter;

import org.springframework.boot.builder.SpringApplicationBuilder;

@FunctionalInterface
public interface SpringApplicationBuilderFactory {

    SpringApplicationBuilder createSpringApplicationBuilder();
}
