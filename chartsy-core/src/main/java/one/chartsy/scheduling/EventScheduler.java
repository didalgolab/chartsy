/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.scheduling;

public interface EventScheduler {

    void schedule(long triggerTime, Runnable event);
}
