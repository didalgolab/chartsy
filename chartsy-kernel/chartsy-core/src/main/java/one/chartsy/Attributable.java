/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import java.util.Map;
import java.util.Optional;

public interface Attributable {

    Map<String, ?> getAttributes();

    @SuppressWarnings("unchecked")
    default <T> Optional<T> getAttribute(AttributeKey<T> key) {
        var value = getAttributes().get(key.name());
        return key.type().isInstance(value)? Optional.of((T) value) : Optional.empty();
    }
}
