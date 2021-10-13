package one.chartsy.simulation;

import one.chartsy.SymbolIdentity;
import one.chartsy.trade.Order;
import one.chartsy.trade.data.TransactionData;
import org.immutables.builder.Builder;
import org.immutables.builder.Builder.AccessibleFields;
import org.immutables.value.Value;
import org.immutables.value.internal.$processor$.meta.$BuilderMirrors;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Value.Immutable
public abstract class SimulationResult {
    public enum State { UNINITIALIZED, RUNNING, READY }

    public abstract LocalDateTime startTime();
    public abstract LocalDateTime endTime();
    public abstract Duration testDuration();
    public abstract long estimatedDataPointCount();
    public abstract Map<SymbolIdentity, List<Order>> remainingOrders();
    public abstract int remainingOrderCount();
    public abstract List<TransactionData> transactions();

    @Value.Default
    public State state() {
        return State.UNINITIALIZED;
    };

    @AccessibleFields
    public static class Builder extends ImmutableSimulationResult.Builder {

        public LocalDateTime getStartTime() {
            return startTime;
        }
    }
}
