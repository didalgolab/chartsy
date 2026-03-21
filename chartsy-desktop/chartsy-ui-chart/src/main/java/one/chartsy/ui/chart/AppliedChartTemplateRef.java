/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import java.util.Objects;
import java.util.UUID;

public record AppliedChartTemplateRef(
        UUID templateKey,
        String name,
        boolean builtIn,
        boolean defaultTemplate) {

    public AppliedChartTemplateRef {
        templateKey = Objects.requireNonNull(templateKey, "templateKey");
        name = Objects.requireNonNull(name, "name");
    }
}
