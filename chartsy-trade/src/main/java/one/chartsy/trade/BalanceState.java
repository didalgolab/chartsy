/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade;

import org.immutables.value.Value;

@Value.Immutable
public interface BalanceState {

    double getEquity();

    BalanceState ZERO = ImmutableBalanceState.builder().build();
}
