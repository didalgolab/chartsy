/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

public enum SysParams {
    ANNOTATION_HANDLE_SIZE(6);

    SysParams(Object value) {
        this(value, "");
    }

    SysParams(Object value, String description) {
        this.value = value;
    }

    private final Object value;

    public int intValue() {
        return ((Number) value).intValue();
    }
}
