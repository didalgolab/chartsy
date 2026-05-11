/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel.starter;

import org.openide.util.Lookup;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.metrics.ApplicationStartup;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractSpringApplicationBuilderFactory implements SpringApplicationBuilderFactory {

    @Override
    public ConfigurableApplicationContext createApplicationContext(ApplicationStartup applicationStartup, String... args) {
        var context = new AnnotationConfigApplicationContext();
        context.setApplicationStartup((applicationStartup != null) ? applicationStartup : ApplicationStartup.DEFAULT);
        context.setAllowBeanDefinitionOverriding(true);

        var classLoader = Lookup.getDefault().lookup(ClassLoader.class);
        if (classLoader != null)
            context.setClassLoader(classLoader);

        var properties = new HashMap<>(createDefaultProperties(args));
        if (!properties.isEmpty())
            context.getEnvironment().getPropertySources().addLast(new MapPropertySource("chartsyDefaults", properties));

        // Preserve global lazy-init without paying SpringApplication startup costs.
        context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor());
        registerConfigurationClasses(context);
        context.refresh();
        return context;
    }

    protected void registerConfigurationClasses(AnnotationConfigApplicationContext context) {
        context.register(getClass());
    }

    protected final void registerConfigurationClass(AnnotationConfigApplicationContext context, String className) {
        try {
            context.register(Class.forName(className, false, context.getClassLoader()));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Missing required configuration class: " + className, e);
        }
    }

    protected Map<String, Object> createDefaultProperties(String[] args) {
        return Map.of();
    }
}
