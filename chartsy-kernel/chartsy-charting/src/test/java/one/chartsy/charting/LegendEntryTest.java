package one.chartsy.charting;

import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LegendEntryTest {

    @Test
    void detached_entry_still_measures_its_label() {
        var entry = new LegendEntry("Item");

        Dimension labelSize = entry.getLabelDimension();

        assertThat(labelSize.width).isPositive();
        assertThat(labelSize.height).isPositive();
    }

    @Test
    void detached_entry_can_draw_its_label() {
        var entry = new LegendEntry("Item");
        var image = new BufferedImage(64, 24, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            assertThatCode(() -> entry.drawLabel(g, new Point2D.Double(32, 12))).doesNotThrowAnyException();
        } finally {
            g.dispose();
        }
    }

    @Test
    void detached_entry_has_no_preferred_size_until_attached() {
        var entry = new LegendEntry("Item");

        assertThat(entry.getPreferredSize()).isEqualTo(new Dimension());
    }
}
