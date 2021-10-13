package one.chartsy.trade.event;

import one.chartsy.trade.Execution;
import one.chartsy.trade.Order;

@FunctionalInterface
public interface ExecutionListener {

    void onExecution(Execution execution, Order order);
}
