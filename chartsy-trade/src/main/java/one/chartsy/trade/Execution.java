package one.chartsy.trade;

import one.chartsy.SymbolIdentity;
import one.chartsy.time.Chronological;

public interface Execution extends Chronological {

    one.chartsy.trade.Order getOrder();

    SymbolIdentity getSymbol();

    Direction getSide();

    String getExecutionId();

    double getPrice();

    double getSize();

    String getExchangeName();

    double getClosingCommission();
}
