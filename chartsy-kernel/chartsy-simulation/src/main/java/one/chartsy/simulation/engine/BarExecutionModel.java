package one.chartsy.simulation.engine;

import one.chartsy.Candle;
import one.chartsy.trade.Order;

/** Executable bid/ask arithmetic. Stops are measured from the actual entry fill. */
final class BarExecutionModel {
    private final double spread;
    BarExecutionModel(double spread) { this.spread = spread; }
    double executionPrice(Order.Side side, double bid) { return bid + (side.isBuy() ? spread : 0); }
    double liquidationPrice(boolean longPosition, double bid) { return bid + (longPosition ? 0 : spread); }
    boolean stopAtOpen(boolean longPosition, double stop, double bidOpen) {
        return longPosition ? bidOpen <= stop : bidOpen + spread >= stop;
    }
    boolean stopTouched(boolean longPosition, double stop, Candle bar) {
        return longPosition ? bar.low() <= stop : bar.high() + spread >= stop;
    }
    double stopFill(boolean longPosition, double stop, double bidOpen) {
        return longPosition ? Math.min(stop, bidOpen) : Math.max(stop, bidOpen + spread);
    }
    boolean takeProfitTouched(boolean longPosition, double takeProfit, Candle bar) {
        return longPosition ? bar.high() >= takeProfit : bar.low() + spread <= takeProfit;
    }
    double takeProfitFill(boolean longPosition, double takeProfit, double bidOpen) {
        return longPosition ? Math.max(takeProfit, bidOpen) : Math.min(takeProfit, bidOpen + spread);
    }
}
