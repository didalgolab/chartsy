package one.chartsy.trade.event;

import one.chartsy.trade.data.Position;
import one.chartsy.trade.data.TransactionData;

/**
 * Receives notifications of changed position on the broker account (either live
 * or simulated).
 * 
 * @author Mariusz Bernacki
 */
public interface PositionChangeListener {

    void positionOpened(Position position);
    
    void positionClosed(Position position, TransactionData transaction);
}
