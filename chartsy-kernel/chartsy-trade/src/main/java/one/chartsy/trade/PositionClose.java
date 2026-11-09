/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade;

import one.chartsy.SymbolIdentity;

/**
 * Closes the source's owned persistent position at the next eligible opening, without replacement.
 * The broker resolves the full quantity and executable exit side from its existing protection.
 * A flat account is an audited no-op; foreign or inconsistent protection is rejected.
 */
public record PositionClose(long time, String orderId, String sourceId, String destinationId,
                            String accountId, SymbolIdentity symbol) implements PositionRequest { }
