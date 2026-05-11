/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import java.util.Objects;

public record StoredParameterValue(String type, String value) {

    public StoredParameterValue {
        type = Objects.requireNonNull(type, "type");
        value = Objects.requireNonNull(value, "value");
    }
}
