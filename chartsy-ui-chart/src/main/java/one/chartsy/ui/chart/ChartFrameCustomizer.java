/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

@FunctionalInterface
public interface ChartFrameCustomizer {

    void customize(ChartFrame cf);
}
