package one.chartsy.trade;

import one.chartsy.SymbolIdentity;
import one.chartsy.trade.data.Position;

import java.util.List;

public interface Instrument {

    SymbolIdentity getSymbol();

    List<Order> orders();

    Position position();

    boolean isActive();

    boolean isActiveSince(long lastTradeTime);

}
