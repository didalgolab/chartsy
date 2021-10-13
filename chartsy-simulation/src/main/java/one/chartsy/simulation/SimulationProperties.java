package one.chartsy.simulation;

import org.immutables.value.Value;

@Value.Immutable
@Value.Style(validationMethod = Value.Style.ValidationMethod.NONE)
public interface SimulationProperties {
    double getInitialBalance();
    double getSpread();
    boolean isAllowSameBarExit();
    boolean isAllowTakeProfitSlippage();
    boolean isCloseAllPositionsAfterSimulation();
    boolean isTransactionHistoryEnabled();
}
