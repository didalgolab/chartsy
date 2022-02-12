package one.chartsy.trade;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.trade.data.Position;
import one.chartsy.trade.event.PositionChangeListener;
import one.chartsy.trade.event.PositionValueChangeListener;

import java.util.List;
import java.util.Map;

public interface Account {

    String getId();

    /**
     * Returns the account equity that is (balance - profit).
     */
    double getEquity();

    /**
     * Returns the portfolio instrument associated with the given {@code symbol}.
     */
    Instrument getInstrument(SymbolIdentity symbol);

    /**
     * Returns the opened position for the specified symbol or <tt>null</tt> if
     * no opened position exists for the given symbol.
     *
     * @return the opened position or <tt>null</tt>
     */
    default Position getPosition(SymbolIdentity symbol) {
        return getInstrument(symbol).position();
    }

    void exitPosition(Position position, Execution execution);

    void enterPosition(Position position, double currPrice, long currTime);

    void updateProfit(SymbolIdentity symbol, Candle ohlc);

    /**
     * Checks if there is opened position (either long or short) on the market
     * identified by the given symbol.
     *
     * @param symbol
     *            the symbol to check among the opened positions
     * @return {@code true} if there exists any position, or {@code false}
     *         otherwise
     */
    default boolean isOnMarket(SymbolIdentity symbol) {
        Position position = getPosition(symbol);
        return (position != null);
    }

    /**
     * Checks if you have a long position on the market with the given symbol.
     *
     * @param symbol
     *            the symbol to check among the opened positions
     * @return {@code true} if there exists a long position, or {@code false}
     *         otherwise
     */
    default boolean isLongOnMarket(SymbolIdentity symbol) {
        Position position = getPosition(symbol);
        return (position != null && position.getDirection() == Direction.LONG);
    }

    /**
     * Checks if you have a short position on the market with the given symbol.
     *
     * @param symbol
     *            the symbol to check among the opened positions
     * @return {@code true} if there exists a short position, or {@code false}
     *         otherwise
     */
    default boolean isShortOnMarket(SymbolIdentity symbol) {
        Position position = getPosition(symbol);
        return (position != null && position.getDirection() == Direction.SHORT);
    }

    Map<SymbolIdentity, List<Order>> getPendingOrders();

    void addPositionChangeListener(PositionChangeListener listener);

    void removePositionChangeListener(PositionChangeListener listener);

    void addPositionValueChangeListener(PositionValueChangeListener listener);

    void removePositionValueChangeListener(PositionValueChangeListener listener);
}
