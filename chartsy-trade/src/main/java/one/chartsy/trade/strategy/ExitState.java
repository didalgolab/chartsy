/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.trade.strategy;

import java.util.Objects;

public record ExitState(ExitCode code, String description) {

    public static final ExitState COMPLETED = new ExitState(ExitCode.COMPLETED, "");
    public static final ExitState STOPPED = new ExitState(ExitCode.STOPPED, "");
    public static final ExitState FAILED = new ExitState(ExitCode.FAILED, "");
    public static final ExitState ABORTED = new ExitState(ExitCode.ABORTED, "");

    public ExitState {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(description, "description");
    }

    public ExitState withDescription(String description) {
        return new ExitState(code, description);
    }
}
