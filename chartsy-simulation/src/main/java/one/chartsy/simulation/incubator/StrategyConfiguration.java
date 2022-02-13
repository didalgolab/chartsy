package one.chartsy.simulation.incubator;

import lombok.Getter;
import one.chartsy.SymbolIdentity;
import one.chartsy.TimeFrame;
import one.chartsy.data.Series;
import org.immutables.value.Value;

import java.util.List;

@Value.Style(depluralize = true, depluralizeDictionary = "series:series")
@Value.Immutable
public interface StrategyConfiguration {

    List<SymbolIdentity> symbols();

    TimeFrame timeFrame();

    List<Series<?>> dataSeries();

    ReportOptions reportOptions();

    static Builder builder() {
        return new Builder();
    }


    @Getter
    class Builder extends ImmutableStrategyConfiguration.Builder {
        private ReportOptions.Builder reportOptions = new ReportOptions.Builder();

        public Builder() {
            timeFrame(TimeFrame.Period.DAILY);
        }

        @Override
        public ImmutableStrategyConfiguration build() {
            reportOptions(reportOptions.build());
            return super.build();
        }
    }
}
