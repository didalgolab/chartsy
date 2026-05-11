package one.chartsy.ui.chart;

import one.chartsy.Candle;
import one.chartsy.FrontEndSupport;
import one.chartsy.SymbolGroup;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DataQuery;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.time.Chronological;
import one.chartsy.ui.chart.type.CandlestickChart;
import org.junit.jupiter.api.Test;
import org.openide.NotifyDescriptor;
import reactor.core.publisher.Flux;

import javax.swing.JLayer;
import java.awt.Color;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ChartFrameLoadFailureRecoveryTest {

    @Test
    void failedLoadRestoresPreviousChartAndKeepsHistoryOnYes() {
        CandleSeries initialDataset = fixtureDataset("RECOVERY-YES");
        SymbolIdentity failingSymbol = SymbolIdentity.of("RECOVERY-YES-MISSING");
        TestChartFrame frame = loadedChartFrame(initialDataset, failingSymbol, NotifyDescriptor.YES_OPTION);

        onEdt(() -> frame.symbolChanged(failingSymbol));

        awaitFailureHandling(frame);

        onEdt(() -> {
            assertThat(frame.dialogCalls()).isEqualTo(1);
            assertDisplayedChart(frame, initialDataset);
            assertThat(frame.getHistory().hasBackHistory()).isFalse();
            assertThat(frame.getHistory().getNextActions()).isEmpty();
            assertThat(frame.getHistory().go(0).getSymbol()).isEqualTo(initialDataset.getResource().symbol());
            assertWaitLayerCleared(frame);
        });
    }

    @Test
    void failedLoadClearsWaitLayerAndDoesNotPolluteHistoryOnNo() {
        CandleSeries initialDataset = fixtureDataset("RECOVERY-NO");
        SymbolIdentity failingSymbol = SymbolIdentity.of("RECOVERY-NO-MISSING");
        TestChartFrame frame = loadedChartFrame(initialDataset, failingSymbol, NotifyDescriptor.NO_OPTION);

        onEdt(() -> frame.symbolChanged(failingSymbol));

        awaitFailureHandling(frame);

        onEdt(() -> {
            assertThat(frame.dialogCalls()).isEqualTo(1);
            assertDisplayedChart(frame, initialDataset);
            assertThat(frame.getHistory().hasBackHistory()).isFalse();
            assertThat(frame.getHistory().getNextActions()).isEmpty();
            assertThat(frame.getHistory().go(0).getSymbol()).isEqualTo(initialDataset.getResource().symbol());
            assertWaitLayerCleared(frame);
        });
    }

    @Test
    void failedBackNavigationRestoresHistoryCursorAndDisplayedChartOnNo() {
        CandleSeries previousDataset = fixtureDataset("RECOVERY-BACK-PREV");
        CandleSeries currentDataset = fixtureDataset("RECOVERY-BACK-CURR");
        TestChartFrame frame = loadedChartFrame(
                previousDataset,
                new FailingNavigationProvider(Map.of(
                        previousDataset.getResource().symbol(), previousDataset,
                        currentDataset.getResource().symbol(), currentDataset
                ), previousDataset.getResource().symbol()),
                NotifyDescriptor.NO_OPTION
        );

        onEdt(() -> {
            frame.datasetLoaded(currentDataset);
            ChartHistoryEntry previousAction = frame.getHistory().go(-1);
            frame.navigationChange(previousAction, 1);
        });

        awaitFailureHandling(frame);

        onEdt(() -> {
            assertThat(frame.dialogCalls()).isEqualTo(1);
            assertDisplayedChart(frame, currentDataset);
            assertThat(frame.getHistory().go(0).getSymbol()).isEqualTo(currentDataset.getResource().symbol());
            assertThat(frame.getHistory().hasBackHistory()).isTrue();
            assertThat(frame.getHistory().hasFwdHistory()).isFalse();
            assertWaitLayerCleared(frame);
        });
    }

    private static void awaitFailureHandling(ChartFrame frame) {
        await().atMost(Duration.ofSeconds(5)).until(() -> !waitLayerActive(frame));
    }

    private static boolean waitLayerActive(ChartFrame frame) {
        return onEdtGet(() -> chartLayer(frame).getUI() instanceof ChartFrame.WaitLayerUI);
    }

    private static JLayer<?> chartLayer(ChartFrame frame) {
        for (var component : frame.getComponents()) {
            if (component instanceof JLayer<?> layer)
                return layer;
        }
        throw new AssertionError("Chart layer not found");
    }

    private static TestChartFrame loadedChartFrame(CandleSeries dataset,
                                                   SymbolIdentity failingSymbol,
                                                   Object dialogResult) {
        return loadedChartFrame(
                dataset,
                new FailingNavigationProvider(Map.of(dataset.getResource().symbol(), dataset), failingSymbol),
                dialogResult
        );
    }

    private static TestChartFrame loadedChartFrame(CandleSeries dataset,
                                                   DataProvider provider,
                                                   Object dialogResult) {
        return onEdtGet(() -> {
            var chartData = new ChartData();
            chartData.setDataProvider(provider);
            chartData.setSymbol(dataset.getResource().symbol());
            chartData.setTimeFrame(dataset.getResource().timeFrame());

            ChartTemplate template = simpleTemplate();
            chartData.setChart(template.getChart());

            var frame = new TestChartFrame(dialogResult);
            frame.setSize(1280, 720);
            frame.setChartData(chartData);
            frame.setChartTemplate(template);
            frame.datasetLoaded(dataset);
            return frame;
        });
    }

    private static void assertDisplayedChart(ChartFrame frame, CandleSeries dataset) {
        assertThat(frame.getChartData().getDataset().getResource()).isEqualTo(dataset.getResource());
        assertThat(frame.getChartData().getSymbol()).isEqualTo(dataset.getResource().symbol());
        assertThat(frame.getChartData().getTimeFrame()).isEqualTo(dataset.getResource().timeFrame());
    }

    private static void assertWaitLayerCleared(ChartFrame frame) {
        assertThat(chartLayer(frame).getUI()).isNotInstanceOf(ChartFrame.WaitLayerUI.class);
    }

    private static void onEdt(Runnable action) {
        onEdtGet(() -> {
            action.run();
            return null;
        });
    }

    private static <T> T onEdtGet(Supplier<T> supplier) {
        return FrontEndSupport.getDefault().execute(supplier::get);
    }

    private static CandleSeries fixtureDataset(String ticker) {
        SymbolIdentity symbol = SymbolIdentity.of(ticker);
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

    private static ChartTemplate simpleTemplate() {
        ChartProperties properties = new ChartProperties();
        properties.setBackgroundColor(Color.WHITE);
        properties.setAxisColor(Color.WHITE);
        properties.setAxisLogarithmicFlag(false);
        properties.setGridHorizontalVisibility(false);
        properties.setGridVerticalVisibility(false);

        ChartTemplate template = new ChartTemplate("Failure Recovery");
        template.setChartProperties(properties);
        template.setChart(new CandlestickChart());
        return template;
    }

    private record FailingNavigationProvider(Map<SymbolIdentity, CandleSeries> datasets,
                                             SymbolIdentity failingSymbol) implements DataProvider {
        @Override
        public String getName() {
            return "Failure Recovery Fixture";
        }

        @Override
        public List<SymbolIdentity> listSymbols(SymbolGroup group) {
            var symbols = new ArrayList<>(datasets.keySet());
            if (!symbols.contains(failingSymbol))
                symbols.add(failingSymbol);
            return List.copyOf(symbols);
        }

        @Override
        public <T extends Chronological> Flux<T> query(Class<T> type, DataQuery<T> request) {
            if (!Candle.class.isAssignableFrom(type))
                return Flux.empty();
            if (failingSymbol.equals(request.resource().symbol()))
                throw new IllegalStateException("Missing dataset for " + failingSymbol.name());
            CandleSeries dataset = datasets.get(request.resource().symbol());
            if (dataset == null)
                return Flux.empty();
            return Flux.fromIterable(dataset).cast(type);
        }
    }

    private static final class TestChartFrame extends ChartFrame {
        private final Object dialogResult;
        private final AtomicInteger dialogCalls = new AtomicInteger();

        private TestChartFrame(Object dialogResult) {
            this.dialogResult = dialogResult;
        }

        int dialogCalls() {
            return dialogCalls.get();
        }

        @Override
        protected Object showDialog(NotifyDescriptor descriptor) {
            dialogCalls.incrementAndGet();
            return dialogResult;
        }
    }
}
