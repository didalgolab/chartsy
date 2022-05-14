/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel.starter;

import org.openide.util.Lookup;
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.io.DefaultResourceLoader;

public abstract class AbstractSpringApplicationBuilderFactory implements SpringApplicationBuilderFactory {

    @Override
    public SpringApplicationBuilder createSpringApplicationBuilder() {
        SpringApplicationBuilder appBuilder = new SpringApplicationBuilder(getClass())
                .lazyInitialization(true)
                .bannerMode(Banner.Mode.OFF)
                .resourceLoader(new DefaultResourceLoader(Lookup.getDefault().lookup(ClassLoader.class)));
        appBuilder.application().setAllowBeanDefinitionOverriding(true);

        // run customizers obtained from default Lookup
        var customizers = Lookup.getDefault().lookupAll(SpringApplicationCustomizer.class);
        if (!customizers.isEmpty())
            for (var customizer : customizers)
                appBuilder = customizer.customize(appBuilder);

        return appBuilder;
    }
}
