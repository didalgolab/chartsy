/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.chartfx.service.execution;

import one.chartsy.samples.chartfx.dos.Order;

public interface ExecutionPlatform {
    /**
   * Execute the trading order
   *
   * @param order Order
   * @return result
   */
    ExecutionResult performOrder(Order order);

    /**
   * Cancel the trading order
   *
   * @param orderId int
   * @return result
   */
    ExecutionResult cancelOrder(int orderId);

    /**
   * Cancel the trading order
   *
   * @param order instance
   * @return result
   */
    ExecutionResult cancelOrder(Order order);

    /**
   * Add the listener of execution platform
   *
   * @param listener ExecutionPlatformListener
   */
    void addExecutionPlatformListener(ExecutionPlatformListener listener);

    /**
   * Remove the listener of execution platform
   *
   * @param listener ExecutionPlatformListener
   */
    void removeExecutionPlatformListener(ExecutionPlatformListener listener);
}
