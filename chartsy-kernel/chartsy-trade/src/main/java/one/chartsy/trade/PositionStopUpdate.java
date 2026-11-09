/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade;

import one.chartsy.SymbolIdentity;

/**
 * Updates only the stop of the source's owned persistent position at the next eligible opening.
 * The broker derives its side from that position and retains its quantity and entry. A flat account
 * is an audited no-op. This request cannot enter, reverse, or close on a directional signal;
 * an installed stop may still execute immediately if the opening quote triggers it.
 *
 * {@link DistanceSource#POSITION_ENTRY} asks the broker to use the distance retained when the
 * current position was opened. Later stop resets do not replace it. The unresolved request
 * distance is {@code NaN}; the broker's audit records the effective distance, or {@code NaN}
 * when no position exists.
 */
public record PositionStopUpdate(long time, String orderId, String sourceId, String destinationId,
                                 String accountId, SymbolIdentity symbol, double stopDistance,
                                 PositionTarget.StopUpdatePolicy stopUpdatePolicy,
                                 DistanceSource distanceSource) implements PositionRequest {
    public PositionStopUpdate {
        if (distanceSource == null)
            throw new IllegalArgumentException("Stop-update distance source is required");
        if (distanceSource == DistanceSource.PROVIDED && (!Double.isFinite(stopDistance) || stopDistance <= 0))
            throw new IllegalArgumentException("Stop-update distance must be positive and finite");
        if (distanceSource == DistanceSource.POSITION_ENTRY && !Double.isNaN(stopDistance))
            throw new IllegalArgumentException("Position-entry distance is resolved by the broker");
    }

    /** Preserves the supplied-distance request API. */
    public PositionStopUpdate(long time, String orderId, String sourceId, String destinationId,
                              String accountId, SymbolIdentity symbol, double stopDistance,
                              PositionTarget.StopUpdatePolicy stopUpdatePolicy) {
        this(time, orderId, sourceId, destinationId, accountId, symbol, stopDistance, stopUpdatePolicy,
                DistanceSource.PROVIDED);
    }

    public static PositionStopUpdate fromPositionEntry(long time, String orderId, String sourceId,
                                                       String destinationId, String accountId, SymbolIdentity symbol,
                                                       PositionTarget.StopUpdatePolicy stopUpdatePolicy) {
        return new PositionStopUpdate(time, orderId, sourceId, destinationId, accountId, symbol,
                Double.NaN, stopUpdatePolicy, DistanceSource.POSITION_ENTRY);
    }

    public enum DistanceSource { PROVIDED, POSITION_ENTRY }
}
