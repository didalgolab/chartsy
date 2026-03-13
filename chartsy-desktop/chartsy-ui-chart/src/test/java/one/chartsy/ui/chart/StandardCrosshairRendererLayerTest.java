package one.chartsy.ui.chart;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.charting.Scale;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.provider.DataProvider;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThat;

class StandardCrosshairRendererLayerTest {

    @Test
    void resolveScaleBoundsRecoversAfterScaleInvalidation() {
        ChartFrame chartFrame = ChartExporter.createChartFrame(
                DataProvider.EMPTY,
                fixtureDataset(),
                ChartTemplateDefaults.basicChartTemplate(),
                new Dimension(1280, 800)
        );
        Scale valueScale = chartFrame.getMainPanel().getChartPanel().getEngineChart().getYScale(0);
        @SuppressWarnings("unchecked")
        JLayer<? extends JComponent> layer = (JLayer<? extends JComponent>) SwingUtilities.getAncestorOfClass(
                JLayer.class,
                chartFrame.getMainPanel()
        );

        valueScale.getChart().invalidateScales();

        assertThat(layer).isNotNull();
        assertThatNoException()
                .isThrownBy(() -> new StandardCrosshairRendererLayer().resolveScaleBounds(valueScale, layer));
    }

    private static CandleSeries fixtureDataset() {
        return CandleSeries.of(
                SymbolResource.of(SymbolIdentity.of("CROSSHAIR-FIXTURE"), TimeFrame.Period.DAILY)
                        .withDataType(Candle.class),
                List.of(
                        Candle.of(LocalDate.of(2026, 1, 1).atStartOfDay(), 100, 104, 98, 103, 1_000),
                        Candle.of(LocalDate.of(2026, 1, 2).atStartOfDay(), 103, 106, 101, 105, 6_000),
                        Candle.of(LocalDate.of(2026, 1, 3).atStartOfDay(), 105, 107, 102, 104, 3_500),
                        Candle.of(LocalDate.of(2026, 1, 4).atStartOfDay(), 104, 108, 103, 107, 8_000),
                        Candle.of(LocalDate.of(2026, 1, 5).atStartOfDay(), 107, 111, 106, 110, 7_400)
                )
        );
    }
}
