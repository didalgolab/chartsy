/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import java.util.Objects;
import java.util.UUID;

public record ChartTemplateSummary(
        UUID templateKey,
        String name,
        boolean defaultTemplate,
        boolean builtIn) {

    public ChartTemplateSummary {
        templateKey = Objects.requireNonNull(templateKey, "templateKey");
        name = Objects.requireNonNull(name, "name");
    }

    public boolean editable() {
        return !builtIn;
    }
}
