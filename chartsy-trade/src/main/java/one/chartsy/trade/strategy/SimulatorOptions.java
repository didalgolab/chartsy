/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.trade.strategy;

import org.immutables.value.Value;

@Value.Style(validationMethod = Value.Style.ValidationMethod.NONE)
@Value.Immutable
public interface SimulatorOptions {

    double initialBalance();

    double spread();

    boolean allowSameBarExit();

    boolean allowTakeProfitSlippage();

    boolean closeAllPositionsAfterSimulation();

    boolean isTransactionHistoryEnabled();

    static Builder builder() {
        return new Builder();
    }

    class Builder extends ImmutableSimulatorOptions.Builder {
        protected Builder() {
            initialBalance(10_000.0);
        }
    }
}
