package one.chartsy.exploration.ui;

import one.chartsy.AttributeKey;
import one.chartsy.Symbol;
import one.chartsy.TimeFrame;
import one.chartsy.kernel.GlobalSymbolSelection;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
public interface ExplorationConfiguration {
    AttributeKey<ExplorationConfiguration> KEY = new AttributeKey<>(ExplorationConfiguration.class);

    List<Symbol> getSymbols();

    TimeFrame getTimeFrame();

    int getDatasetMinDataPoints();

    static ExplorationConfiguration currentSnapshot() {
        return ImmutableExplorationConfiguration.builder()
                .datasetMinDataPoints(1)
                .timeFrame(TimeFrame.Period.DAILY)
                .symbols(GlobalSymbolSelection.get().selectedSymbols())
                .build();
    }
}
