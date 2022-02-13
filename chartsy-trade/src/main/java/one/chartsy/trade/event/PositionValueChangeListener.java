package one.chartsy.trade.event;

import one.chartsy.trade.Account;
import one.chartsy.trade.data.Position;

/**
 * Receives notifications of changed position value as a result of market price change
 * (either in a live or simulated account).
 *
 * @author Mariusz Bernacki
 */
public interface PositionValueChangeListener {

    void positionValueChanged(Account account, Position position);
}
