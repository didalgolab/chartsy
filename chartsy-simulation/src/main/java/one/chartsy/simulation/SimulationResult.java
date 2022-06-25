/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.simulation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import one.chartsy.simulation.reporting.Report;
import one.chartsy.trade.Order;
import one.chartsy.trade.data.TransactionData;
import org.immutables.builder.Builder.AccessibleFields;
import org.immutables.value.Value;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Value.Style(
        of = "new", // renames "of" method to "new", which is interpreted as plain constructor
        allParameters = true, // unrelated to the line above: every attribute becomes parameter
        // reminder: don't get used to inline styles, read style guide!
        passAnnotations = {JsonCreator.class, JsonProperty.class},
        privateNoargConstructor = true
)
@Value.Immutable
//@JsonDeserialize
//@JsonSerialize
public abstract class SimulationResult {
    public enum State { UNINITIALIZED, RUNNING, READY }

    @JsonCreator
    SimulationResult() { }

    @JsonProperty("uid")
    public abstract UUID uid();
    @JsonProperty("startTime")
    public abstract LocalDateTime startTime();
    @JsonProperty("endTime")
    public abstract LocalDateTime endTime();
    @JsonProperty("testDuration")
    public abstract Duration testDuration();
    @JsonProperty("testDays")
    public abstract int testDays();
    @JsonProperty("estimatedDataPointCount")
    public abstract long estimatedDataPointCount();
    @JsonProperty("totalProfit")
    public abstract double totalProfit();
    @JsonProperty("remainingOrders")
    public abstract List<Order> remainingOrders();
    @JsonProperty("remainingOrderCount")
    public abstract int remainingOrderCount();
    @JsonProperty("transactions")
    public abstract List<TransactionData> transactions();
    @JsonProperty("report")
    public abstract Report report();

    @Value.Default
    @JsonProperty("state")
    public State state() {
        return State.UNINITIALIZED;
    };

    @AccessibleFields
    public static class Builder extends ImmutableSimulationResult.Builder {
        public Builder() {
            uid(UUID.randomUUID());
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }
    }
}
