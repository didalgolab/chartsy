/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.trade.strategy;

public enum ExitCode {
    COMPLETED,
    STOPPED,
    FAILED,
    ABORTED;

    public boolean isNormalExit() {
        return (COMPLETED == this);
    }

    public boolean isForcedExit() {
        return !isNormalExit();
    }
}
