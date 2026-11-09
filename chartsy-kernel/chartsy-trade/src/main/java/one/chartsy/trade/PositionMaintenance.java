package one.chartsy.trade;

import one.chartsy.SymbolIdentity;

/**
 * Maintains an owned persistent position at the next eligible opening without permitting an entry.
 * A same-direction signal retains quantity and applies the stop policy; an opposite signal closes
 * the position without replacement. A flat account is an audited no-op. Ownership is checked by
 * the broker against the source, account and symbol when the request executes.
 */
public record PositionMaintenance(long time, String orderId, String sourceId, String destinationId,
                                  String accountId, SymbolIdentity symbol, Order.Side signalSide,
                                  double stopDistance, PositionTarget.StopUpdatePolicy stopUpdatePolicy)
        implements PositionRequest {
    public PositionMaintenance {
        if (!Double.isFinite(stopDistance) || stopDistance <= 0)
            throw new IllegalArgumentException("Maintenance stop distance must be positive and finite");
    }
}
