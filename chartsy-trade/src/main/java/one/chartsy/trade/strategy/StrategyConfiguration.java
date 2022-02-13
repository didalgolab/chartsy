package one.chartsy.trade.strategy;

import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.Getter;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.Series;
import one.chartsy.data.provider.DataProvider;
import org.immutables.value.Value;

import java.util.List;
import java.util.Map;

@Value.Style(depluralize = true, depluralizeDictionary = "series:series")
@Value.Immutable
public interface StrategyConfiguration {

    List<SymbolIdentity> symbols();

    TimeFrame timeFrame();

    List<SymbolResource<?>> dataResources();

    @Nullable DataProvider dataProvider();

    List<Series<?>> dataSeries();

    ReportOptions reportOptions();

    SimulatorOptions simulatorOptions();

    Map<String, Object> inputParameters();

    static Builder builder() {
        return new Builder();
    }


    @Getter
    class Builder extends ImmutableStrategyConfiguration.Builder {
        private ReportOptions.Builder reportOptions = new ReportOptions.Builder();
        private SimulatorOptions.Builder simulatorOptions = SimulatorOptions.builder();

        public Builder() {
            timeFrame(TimeFrame.Period.DAILY);
        }

        @Override
        public ImmutableStrategyConfiguration build() {
            reportOptions(reportOptions.build());
            simulatorOptions(simulatorOptions.build());
            return super.build();
        }
    }
}
