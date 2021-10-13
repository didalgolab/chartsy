package one.chartsy.trade;

import one.chartsy.Symbol;
import one.chartsy.trade.data.Position;

import java.util.List;

public interface Instrument {

    Symbol getSymbol();

    List<Order> orders();

    Position position();

    void setPosition(Position position);
}
