package one.chartsy.charting;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Insets;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class LegendSeparatorTest {

    @Test
    void getBorderInsets_tracks_the_chart_facing_edge_for_docked_legends() {
        var separator = new LegendSeparator(Color.RED, 2, 3, 4);

        assertAll(
                () -> assertThat(separator.getBorderInsets(legendAt(ChartLayout.NORTH_BOTTOM)))
                        .isEqualTo(new Insets(0, 0, 9, 0)),
                () -> assertThat(separator.getBorderInsets(legendAt(ChartLayout.SOUTH_TOP)))
                        .isEqualTo(new Insets(9, 0, 0, 0)),
                () -> assertThat(separator.getBorderInsets(legendAt(ChartLayout.WEST)))
                        .isEqualTo(new Insets(0, 0, 0, 9)),
                () -> assertThat(separator.getBorderInsets(legendAt(ChartLayout.EAST)))
                        .isEqualTo(new Insets(0, 9, 0, 0))
        );
    }

    @Test
    void getBorderInsets_for_non_legend_components_uses_only_thickness_and_inside_margin() {
        var separator = new LegendSeparator(Color.RED, 2, 3, 4);

        assertThat(separator.getBorderInsets(new LegendEntry("Item"))).isEqualTo(new Insets(5, 5, 5, 5));
    }

    @Test
    void paintBorder_draws_only_the_chart_facing_edge_for_docked_legends() {
        var separator = new LegendSeparator(Color.RED, 1, 0, 1);
        var image = new BufferedImage(6, 5, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            separator.paintBorder(legendAt(ChartLayout.WEST), g, 0, 0, image.getWidth(), image.getHeight());
        } finally {
            g.dispose();
        }

        assertAll(
                () -> assertThat(image.getRGB(4, 0)).isEqualTo(Color.RED.getRGB()),
                () -> assertThat(image.getRGB(4, 4)).isEqualTo(Color.RED.getRGB()),
                () -> assertThat(image.getRGB(0, 0)).isZero(),
                () -> assertThat(image.getRGB(5, 0)).isZero()
        );
    }

    private static Legend legendAt(String position) {
        var legend = new Legend();
        legend.putClientProperty("_DragPosition_Key__", position);
        return legend;
    }
}
