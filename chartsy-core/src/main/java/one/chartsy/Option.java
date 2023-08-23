/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

/**
 * A style option associated with the {@code StyledValue}.
 */
public interface Option<T> {

    String name();

    Class<T> type();

    static <T> Option<T> of(Class<T> type) {
        return of(type.getSimpleName(), type);
    }

    static <T> Option<T> of(String name, Class<T> type) {
        return new Of<>(name, type);
    }

    record Of<T>(String name, Class<T> type) implements Option<T> { }
}
