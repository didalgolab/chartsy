/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.json;

public interface JsonFormatter {
    String toJson(Object src);
    <T> T fromJson(String json, Class<T> resultType);
}
