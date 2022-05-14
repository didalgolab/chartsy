/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import java.util.function.Supplier;

public record Attribute<T>(AttributeKey<T> key, Supplier<T> valueDefault) {

    public T get(Attributable obj) {
        return obj.getAttribute(key).orElseGet(valueDefault);
    }
}
