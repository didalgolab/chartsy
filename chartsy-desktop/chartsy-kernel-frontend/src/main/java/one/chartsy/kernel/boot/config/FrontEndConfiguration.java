/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel.boot.config;

import one.chartsy.kernel.starter.AbstractSpringApplicationBuilderFactory;
import org.openide.util.lookup.ServiceProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

@Configuration
@ServiceProvider(service = FrontEndConfiguration.class)
public class FrontEndConfiguration extends AbstractSpringApplicationBuilderFactory {

    @Override
    protected void registerConfigurationClasses(AnnotationConfigApplicationContext context) {
        super.registerConfigurationClasses(context);
        registerConfigurationClass(context, "one.chartsy.ui.chart.ChartAutoConfiguration");
        registerConfigurationClass(context, "one.chartsy.ui.config.GuiFrontEndAutoConfiguration");
    }
}
