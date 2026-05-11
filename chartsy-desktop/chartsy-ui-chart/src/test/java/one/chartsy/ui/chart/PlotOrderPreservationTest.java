package one.chartsy.ui.chart;

import one.chartsy.ui.chart.plot.AbstractPlot;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlotOrderPreservationTest {

    @Test
    void indicatorPlotsPreserveInsertionOrder() {
        var indicator = new StubIndicator();
        indicator.addPlot("first", new StubPlot(Color.RED));
        indicator.addPlot("second", new StubPlot(Color.GREEN));
        indicator.addPlot("third", new StubPlot(Color.BLUE));

        assertEquals(List.of("first", "second", "third"), List.copyOf(indicator.getPlots().keySet()));
    }

    @Test
    void overlayPlotsPreserveInsertionOrder() {
        var overlay = new StubOverlay();
        overlay.addPlot("neutral", new StubPlot(Color.LIGHT_GRAY));
        overlay.addPlot("high", new StubPlot(Color.DARK_GRAY));
        overlay.addPlot("line", new StubPlot(Color.CYAN));

        assertEquals(List.of("neutral", "high", "line"), List.copyOf(overlay.getPlots().keySet()));
    }

    private static final class StubIndicator extends Indicator {
        private StubIndicator() {
            super("Stub Indicator");
        }

        @Override
        public Indicator newInstance() {
            return new StubIndicator();
        }

        @Override
        public String getLabel() {
            return "Stub Indicator";
        }

        @Override
        public void calculate() {
        }

        @Override
        public boolean getMarkerVisibility() {
            return false;
        }

        @Override
        public double[] getStepValues(ChartContext cf) {
            return new double[0];
        }
    }

    private static final class StubOverlay extends Overlay {
        private StubOverlay() {
            super("Stub Overlay");
        }

        @Override
        public Overlay newInstance() {
            return new StubOverlay();
        }

        @Override
        public String getLabel() {
            return "Stub Overlay";
        }

        @Override
        public void calculate() {
        }

        @Override
        public boolean getMarkerVisibility() {
            return false;
        }
    }

    private static final class StubPlot extends AbstractPlot {
        private StubPlot(Color primaryColor) {
            super(primaryColor);
        }

        @Override
        public void render(PlotRenderTarget target, PlotRenderContext context) {
        }
    }
}
