package one.chartsy.trade;

import one.chartsy.SymbolIdentity;

/**
 * Requests a persistent directional position with a stop and an optional take profit at the next eligible opening.
 * A new entry risks {@code riskFraction * currentEquity} at its initial stop, before gap risk.
 * Existing positions in the requested direction retain their quantity. The execution engine converts
 * the initial stop loss per unit to account currency using its configured profit conversion.
 * {@code takeProfitDistance == 0} disables the take profit for an entry/reset; positive finite
 * distances are measured from the same executable entry-side quote as the stop. Neither
 * distance is an absolute price or a model label. Broker validation rejects malformed requests.
 */
public record PositionTarget(long time, String orderId, String sourceId, String destinationId,
                             String accountId, SymbolIdentity symbol, Order.Side side,
                             double riskFraction, double stopDistance, StopUpdatePolicy stopUpdatePolicy,
                             double takeProfitDistance, IntrabarExitPolicy intrabarExitPolicy)
        implements PositionRequest {
    /** Source-compatible stop-only request: no take profit, stop first for ambiguous candles. */
    public PositionTarget(long time, String orderId, String sourceId, String destinationId,
                          String accountId, SymbolIdentity symbol, Order.Side side,
                          double riskFraction, double stopDistance, StopUpdatePolicy stopUpdatePolicy) {
        this(time, orderId, sourceId, destinationId, accountId, symbol, side, riskFraction,
                stopDistance, stopUpdatePolicy, 0, IntrabarExitPolicy.STOP_FIRST);
    }

    /** Applies only when both barriers touch in one OHLC range; ordered quotes ignore it. */
    public enum IntrabarExitPolicy { STOP_FIRST, TAKE_PROFIT_FIRST }

    public enum StopUpdatePolicy {
        /** Keep both current absolute barriers on a same-direction signal. */
        KEEP,
        /** Atomically reset both barriers from the executable entry-side opening quote; zero TP removes it. */
        RESET_FROM_OPEN,
        /** Tighten only the stop, retaining any held TP; this request must have zero TP distance. */
        TIGHTEN_FROM_OPEN
    }
}
