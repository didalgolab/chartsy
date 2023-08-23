/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.trade.event;

import one.chartsy.trade.Execution;

@FunctionalInterface
public interface ExecutionListener {

    void onExecution(Execution execution);
}
