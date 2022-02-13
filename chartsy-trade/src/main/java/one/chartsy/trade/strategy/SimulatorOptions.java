package one.chartsy.trade.strategy;

import org.immutables.value.Value;

@Value.Style(validationMethod = Value.Style.ValidationMethod.NONE)
@Value.Immutable
public interface SimulatorOptions {

    double initialBalance();

    double spread();

    boolean allowSameBarExit();

    boolean allowTakeProfitSlippage();

    boolean closeAllPositionsAfterSimulation();

    boolean isTransactionHistoryEnabled();

    static Builder builder() {
        return new Builder();
    }


    class Builder extends ImmutableSimulatorOptions.Builder { }
}
