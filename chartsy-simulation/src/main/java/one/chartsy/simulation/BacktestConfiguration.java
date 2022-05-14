/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.simulation;

import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.Getter;
import one.chartsy.TimeFrame;
import one.chartsy.trade.strategy.ReportOptions;
import one.chartsy.trade.strategy.SimulatorOptions;
import one.chartsy.trade.strategy.StrategyConfiguration;
import org.immutables.builder.Builder.AccessibleFields;
import org.immutables.value.Value;

import java.time.LocalDateTime;
import java.util.function.Consumer;

@Value.Style(depluralize = true, depluralizeDictionary = "series:series")
@Value.Immutable
public interface BacktestConfiguration extends StrategyConfiguration {

    @Nullable LocalDateTime startTime();

    @Nullable LocalDateTime endTime();

    static Builder builder() {
        return new BacktestConfiguration.Builder();
    }


    @AccessibleFields
    @Getter
    class Builder extends ImmutableBacktestConfiguration.Builder {
        private ReportOptions.OptionsBuilder<Builder> reportOptions = new ReportOptions.OptionsBuilder<>(this);
        private SimulatorOptions.Builder simulatorOptions = SimulatorOptions.builder();

        public Builder() {
            timeFrame(TimeFrame.Period.DAILY);
        }

        public Builder reportOptions(Consumer<ReportOptions.Builder> opt) {
            opt.accept(reportOptions);
            return this;
        }

        public Builder simulatorOptions(Consumer<SimulatorOptions.Builder> opt) {
            opt.accept(simulatorOptions);
            return this;
        }

        @Override
        public ImmutableBacktestConfiguration build() {
            reportOptions(reportOptions.build());
            simulatorOptions(simulatorOptions.build());
            return super.build();
        }
    }

    class Usage {
        public static void main(String[] args) {
            var o = new BacktestConfiguration.Builder().reportOptions(opt -> opt.addEquityChart())
                    .build()
            ;
            System.out.println(o);
        }
    }
}
