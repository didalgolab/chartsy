package one.chartsy.simulation.incubator;

import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.Getter;
import one.chartsy.TimeFrame;
import org.immutables.builder.Builder.AccessibleFields;
import org.immutables.value.Value;

import java.time.LocalDateTime;
import java.util.function.Consumer;

@Value.Immutable
public interface BacktestConfiguration extends StrategyConfiguration {

    @Nullable LocalDateTime startTime();

    @Nullable LocalDateTime endTime();

    @AccessibleFields
    @Getter
    class Builder extends ImmutableBacktestConfiguration.Builder {
        private ReportOptions.OptionsBuilder<Builder> reportOptions = new ReportOptions.OptionsBuilder<>(this);

        public Builder() {
            timeFrame(TimeFrame.Period.DAILY);
        }

        public Builder reportOptions(Consumer<ReportOptions.Builder> opt) {
            opt.accept(reportOptions);
            return this;
        }

        @Override
        public ImmutableBacktestConfiguration build() {
            reportOptions(reportOptions.build());
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
