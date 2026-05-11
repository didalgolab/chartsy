/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui;

import java.util.UUID;

public record ChartOpenOptions(String chartTypeName, UUID templateKey) {
    public static final ChartOpenOptions DEFAULT = new ChartOpenOptions(null, null);

    public ChartOpenOptions {
        chartTypeName = normalize(chartTypeName);
    }

    public boolean usesDefaultTemplate() {
        return templateKey == null;
    }

    public String chartTypeNameOrDefault(String defaultChartTypeName) {
        return (chartTypeName != null) ? chartTypeName : defaultChartTypeName;
    }

    private static String normalize(String value) {
        if (value == null)
            return null;
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
