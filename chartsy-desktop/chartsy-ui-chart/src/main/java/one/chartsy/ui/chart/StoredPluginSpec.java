/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.SequencedMap;

public record StoredPluginSpec(
        String descriptorId,
        String className,
        String name,
        LinkedHashMap<String, StoredParameterValue> parameters) {

    public StoredPluginSpec {
        className = normalize(className);
        name = Objects.requireNonNull(name, "name");
        parameters = new LinkedHashMap<>(Objects.requireNonNullElseGet(parameters, LinkedHashMap::new));
    }

    public SequencedMap<String, StoredParameterValue> parametersView() {
        return Collections.unmodifiableSequencedMap(new LinkedHashMap<>(parameters));
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
