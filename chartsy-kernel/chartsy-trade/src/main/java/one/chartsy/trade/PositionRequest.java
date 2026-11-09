package one.chartsy.trade;

import one.chartsy.SymbolIdentity;
import one.chartsy.trade.data.OrderRequestEvent;

/**
 * A request addressed to the persistent position which one source owns in an account and symbol.
 * The broker checks that ownership when the request executes, hence every request names all three.
 */
public sealed interface PositionRequest extends OrderRequestEvent
        permits PositionTarget, PositionMaintenance, PositionClose, PositionStopUpdate {

    /** The owning account; {@code null} selects the broker's default account. */
    String accountId();

    SymbolIdentity symbol();
}
