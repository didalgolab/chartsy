/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

public record AttributeKey<T>(Class<T> type, String name) implements Comparable<AttributeKey<?>> {

    public AttributeKey(Class<T> type) {
        this(type, type.getSimpleName());
    }

    @Override
    public int compareTo(AttributeKey<?> o) {
        int cmp = name.compareTo(o.name);
        return (cmp != 0)? cmp: type.getName().compareTo(o.type.getName());
    }
}
