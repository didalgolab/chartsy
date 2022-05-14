/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel.boot.config;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;

public class AutoConfigurationKernelExclusionFilter implements AutoConfigurationImportFilter {

    private static final String BASE_PACKAGE = "one.chartsy";

    @Override
    public boolean[] match(String[] classNames, AutoConfigurationMetadata metadata) {
        boolean[] matches = new boolean[classNames.length];
        for (int i = 0; i < classNames.length; i++)
            matches[i] = accept(classNames[i]);

        return matches;
    }

    protected boolean accept(String className) {
        if (className.startsWith(BASE_PACKAGE)) {
            return className.startsWith(".ui", BASE_PACKAGE.length())
                    || className.startsWith(".frontend", BASE_PACKAGE.length());
        }
        return true;
    }
}
