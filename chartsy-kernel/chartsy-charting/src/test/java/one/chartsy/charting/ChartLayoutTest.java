package one.chartsy.charting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class ChartLayoutTest {

    @Test
    void computeBounds_gives_corner_aware_bands() {
        var fixture = newCornerFixture(0, 3);
        var bounds = new Rectangle(fixture.chart().getSize());

        var computed = fixture.layout().computeBounds(fixture.chart(), bounds);
        assertAll(
                () -> assertThat(fixture.chart().getClientProperty(ServerSideLayout.BOUNDS_PROPERTY)).isSameAs(bounds),
                () -> assertThat(computed).containsOnlyKeys(
                        fixture.northTop(),
                        fixture.northBottom(),
                        fixture.southTop(),
                        fixture.southBottom(),
                        fixture.west(),
                        fixture.east(),
                        fixture.center()
                ),
                () -> assertThat(computed.get(fixture.northTop())).isEqualTo(new Rectangle(22, 1, 159, 10)),
                () -> assertThat(computed.get(fixture.northBottom())).isEqualTo(new Rectangle(22, 14, 159, 7)),
                () -> assertThat(computed.get(fixture.southTop())).isEqualTo(new Rectangle(2, 90, 194, 13)),
                () -> assertThat(computed.get(fixture.southBottom())).isEqualTo(new Rectangle(2, 106, 194, 11)),
                () -> assertThat(computed.get(fixture.west())).isEqualTo(new Rectangle(2, 1, 20, 98)),
                () -> assertThat(computed.get(fixture.east())).isEqualTo(new Rectangle(181, 1, 15, 98)),
                () -> assertThat(computed.get(fixture.center())).isEqualTo(new Rectangle(22, 24, 159, 63))
        );
    }

    @Test
    void layoutContainer_mirrors_computed_bounds() {
        var fixture = newCornerFixture(0, 3);
        var expected = fixture.layout().computeBounds(fixture.chart(), new Rectangle(fixture.chart().getSize()));

        fixture.layout().layoutContainer(fixture.chart());
        var checks = expected.entrySet().stream()
                .<Executable>map(entry ->
                        () -> assertThat(entry.getKey().getBounds()).isEqualTo(entry.getValue()))
                .toArray(Executable[]::new);
        assertAll(checks);
    }

    @Test
    void preferredLayoutSize_gives_insets_aware_span() {
        var layout = new ChartLayout();
        layout.horizontalGap = 5;
        layout.verticalGap = 3;

        var chart = chart(layout, 240, 140, new Insets(1, 2, 3, 4));
        chart.add(panel(50, 10, 40, 8), ChartLayout.NORTH_TOP);
        chart.add(panel(60, 11, 55, 9), ChartLayout.SOUTH_BOTTOM);
        chart.add(panel(70, 30, 65, 25), ChartLayout.CENTER);
        chart.add(panel(20, 40, 18, 35), ChartLayout.WEST);
        chart.add(panel(15, 35, 14, 31), ChartLayout.EAST);

        assertThat(layout.preferredLayoutSize(chart)).isEqualTo(new Dimension(121, 61));
    }

    @Test
    void minimumLayoutSize_gives_insets_aware_span() {
        var layout = new ChartLayout();
        layout.horizontalGap = 5;
        layout.verticalGap = 3;

        var chart = chart(layout, 240, 140, new Insets(1, 2, 3, 4));
        chart.add(panel(50, 10, 40, 8), ChartLayout.NORTH_TOP);
        chart.add(panel(60, 11, 55, 9), ChartLayout.SOUTH_BOTTOM);
        chart.add(panel(70, 30, 65, 25), ChartLayout.CENTER);
        chart.add(panel(20, 40, 18, 35), ChartLayout.WEST);
        chart.add(panel(15, 35, 14, 31), ChartLayout.EAST);

        assertThat(layout.minimumLayoutSize(chart)).isEqualTo(new Dimension(113, 52));
    }

    @Test
    void addLayoutComponent_tracks_only_supported_slots() {
        var layout = new ChartLayout();
        var chart = chart(layout, 120, 80, new Insets(0, 0, 0, 0));
        var west = panel(20, 30);
        var northWest = panel(18, 24);
        var center = panel(40, 20);
        var ignored = panel(10, 10);

        chart.add(west, ChartLayout.WEST);
        chart.add(center, ChartLayout.CENTER);
        chart.add(northWest, ChartLayout.NORTH_WEST);
        chart.add(ignored, ChartLayout.ABSOLUTE);
        layout.addLayoutComponent(ignored, 7);

        var computed = layout.computeBounds(chart, new Rectangle(chart.getSize()));
        assertAll(
                () -> assertThat(layout.getConstraint(west)).isNull(),
                () -> assertThat(layout.getConstraint(center)).isEqualTo(ChartLayout.CENTER),
                () -> assertThat(layout.getConstraint(northWest)).isEqualTo(ChartLayout.NORTH_WEST),
                () -> assertThat(layout.getConstraint(ignored)).isNull(),
                () -> assertThat(computed).containsOnlyKeys(northWest, center),
                () -> assertThat(chart.getComponents()).contains(west, center, northWest, ignored)
        );
    }

    private static LayoutFixture newCornerFixture(int horizontalGap, int verticalGap) {
        var layout = new ChartLayout();
        layout.horizontalGap = horizontalGap;
        layout.verticalGap = verticalGap;
        var chart = chart(layout, 200, 120, new Insets(1, 2, 3, 4));
        var northTop = panel(100, 10);
        var northBottom = panel(100, 7);
        var southTop = panel(100, 13);
        var southBottom = panel(100, 11);
        var west = panel(20, 30);
        var east = panel(15, 30);
        var center = panel(60, 40);

        chart.add(northTop, ChartLayout.NORTH_TOP);
        chart.add(northBottom, ChartLayout.NORTH_BOTTOM);
        chart.add(southTop, ChartLayout.SOUTH_TOP);
        chart.add(southBottom, ChartLayout.SOUTH_BOTTOM);
        chart.add(west, ChartLayout.NORTH_WEST);
        chart.add(east, ChartLayout.NORTH_EAST);
        chart.add(center, ChartLayout.CENTER);

        return new LayoutFixture(layout, chart, northTop, northBottom, southTop, southBottom, west, east, center);
    }

    private static JPanel chart(ChartLayout layout, int width, int height, Insets insets) {
        var chart = new JPanel(layout);
        chart.setBorder(new EmptyBorder(insets));
        chart.setSize(width, height);
        return chart;
    }

    private static JPanel panel(int preferredWidth, int preferredHeight) {
        return panel(preferredWidth, preferredHeight, preferredWidth, preferredHeight);
    }

    private static JPanel panel(int preferredWidth, int preferredHeight, int minimumWidth, int minimumHeight) {
        var panel = new JPanel();
        panel.setPreferredSize(new Dimension(preferredWidth, preferredHeight));
        panel.setMinimumSize(new Dimension(minimumWidth, minimumHeight));
        return panel;
    }

    private record LayoutFixture(
            ChartLayout layout,
            JPanel chart,
            JPanel northTop,
            JPanel northBottom,
            JPanel southTop,
            JPanel southBottom,
            JPanel west,
            JPanel east,
            JPanel center
    ) {
    }
}
