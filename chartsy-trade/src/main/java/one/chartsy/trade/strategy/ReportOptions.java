/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.trade.strategy;

import one.chartsy.AttributeKey;
import org.immutables.value.Value;

import java.util.Set;

@Value.Immutable
public interface ReportOptions {
    AttributeKey<Void> EQUITY = new AttributeKey<>(Void.class, "EQUITY");
    AttributeKey<Void> EQUITY_CHART = new AttributeKey<>(Void.class, "EQUITY_CHART");

    Set<AttributeKey<?>> getEnabled();


    class Builder extends ImmutableReportOptions.Builder {

        public Builder() {
            addEquity();
        }

        public Builder addEquity() {
            return addEnabled(EQUITY);
        }

        public Builder addEquityChart() {
            return addEnabled(EQUITY_CHART);
        }
    }

    class OptionsBuilder<O> extends Builder {
        private final O outer;

        public OptionsBuilder(O outer) {
            this.outer = outer;
        }

        public O endOptions() {
            return outer;
        }
    }
}
