package one.chartsy.simulation.engine;

import one.chartsy.Candle;
import one.chartsy.Symbol;
import one.chartsy.trade.Instrument;
import one.chartsy.trade.Order;
import one.chartsy.trade.data.Position;

import java.util.ArrayList;
import java.util.List;

public class StandardInstrument implements Instrument {
    private final Symbol symbol;
    private final List<Order> orders = new ArrayList<>();
    private final List<Order> transmitQueue = new ArrayList<>();
    private Position position;
    private Candle lastCandle;


    public StandardInstrument(Symbol symbol) {
        this.symbol = symbol;
    }

    @Override
    public Symbol getSymbol() {
        return symbol;
    }

    @Override
    public List<Order> orders() {
        return orders;
    }

    public List<Order> getTransmitQueue() {
        return transmitQueue;
    }

    @Override
    public Position position() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public Candle lastCandle() {
        return lastCandle;
    }

    public void setLastCandle(Candle lastCandle) {
        this.lastCandle = lastCandle;
    }
}
