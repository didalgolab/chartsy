package one.chartsy.ui.chart;

import one.chartsy.Candle;
import one.chartsy.SymbolGroup;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DataQuery;
import one.chartsy.data.provider.DataProvider;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.awt.Dimension;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChartFrameGroupingTest {

    @Test
    void chartFrameGroupsOwnPanelIndicatorsWithSharedPanelIdIntoOnePane() {
        CandleSeries dataset = fixtureDataset();
        ChartTemplate template = ChartTemplateDefaults.basicChartTemplate();
        assertThat(template.getIndicators()).hasSize(1);

        UUID sharedPaneId = UUID.fromString("00000000-0000-0000-0000-000000000321");
        template.getIndicators().forEach(indicator -> indicator.setPanelId(sharedPaneId));

        Indicator extra = StudyRegistry.getDefault().getIndicator("Fractal Dimension");
        assertThat(extra).isNotNull();
        extra.setPanelId(sharedPaneId);
        template.addIndicator(extra);

        ChartFrame frame = ChartExporter.createChartFrame(
                new FixtureProvider(dataset),
                dataset,
                template,
                new Dimension(1280, 720),
                true
        );

        var panes = frame.getMainStackPanel().getIndicatorPanels();
        assertThat(panes).hasSize(1);
        assertThat(panes.getFirst().getId()).isEqualTo(sharedPaneId);
        assertThat(panes.getFirst().getIndicators())
                .extracting(Indicator::getPanelId)
                .containsOnly(sharedPaneId);
        assertThat(panes.getFirst().getIndicators()).hasSize(2);
    }

    private static CandleSeries fixtureDataset() {
        SymbolIdentity symbol = SymbolIdentity.of("GROUPING-FIXTURE");
        SymbolResource<Candle> resource = SymbolResource.of(symbol, TimeFrame.Period.DAILY);
        var candles = new ArrayList<Candle>(220);
        double close = 118.0;
        LocalDate date = LocalDate.of(2024, 1, 2);
        for (int i = 0; i < 220; i++) {
            double drift = Math.sin(i / 7.5d) * 1.4d + Math.cos(i / 15.0d) * 0.6d;
            double open = close + Math.sin(i / 5.0d) * 0.55d;
            close = Math.max(45.0d, open + drift);
            double high = Math.max(open, close) + 0.7d + Math.abs(Math.sin(i / 3.0d));
            double low = Math.min(open, close) - 0.7d - Math.abs(Math.cos(i / 4.0d));
            double volume = 1_500_000d + (i % 17) * 85_000d + Math.abs(drift) * 120_000d;
            candles.add(Candle.of(date.plusDays(i).atStartOfDay(), open, high, low, close, volume));
        }
        return CandleSeries.of(resource, candles);
    }

    private record FixtureProvider(CandleSeries dataset) implements DataProvider {
        @Override
        public String getName() {
            return "Grouping Fixture";
        }

        @Override
        public List<SymbolIdentity> listSymbols(SymbolGroup group) {
            return List.of(dataset.getResource().symbol());
        }

        @Override
        public <T extends one.chartsy.time.Chronological> Flux<T> query(Class<T> type, DataQuery<T> request) {
            if (!type.isAssignableFrom(Candle.class))
                return Flux.empty();
            if (!dataset.getResource().symbol().equals(request.resource().symbol()))
                return Flux.empty();
            return Flux.fromIterable(dataset).cast(type);
        }
    }
}
