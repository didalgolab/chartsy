package one.chartsy.trade.event;

import one.chartsy.trade.Execution;

@FunctionalInterface
public interface ExecutionListener {

    void onExecution(Execution execution);
}
