package one.chartsy.kernel.starter;

import org.springframework.boot.builder.SpringApplicationBuilder;

@FunctionalInterface
public interface SpringApplicationCustomizer {

    SpringApplicationBuilder customize(SpringApplicationBuilder appBuilder);
}
