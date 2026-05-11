package one.chartsy.charting.util.swing;

import java.awt.Dimension;
import java.awt.Rectangle;

import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class VerticalFlowLayoutTest {

    @Test
    void layoutContainer_wraps_components_into_additional_columns() {
        var layout = new VerticalFlowLayout(VerticalFlowLayout.TOP, 5, 3);
        var container = panel(layout, 60, 20);
        var first = child(10, 4);
        var second = child(12, 5);
        var third = child(8, 6);
        container.add(first);
        container.add(second);
        container.add(third);

        layout.layoutContainer(container);

        assertAll(
                () -> assertThat(first.getBounds()).isEqualTo(new Rectangle(2, 4, 10, 4)),
                () -> assertThat(second.getBounds()).isEqualTo(new Rectangle(2, 11, 12, 5)),
                () -> assertThat(third.getBounds()).isEqualTo(new Rectangle(19, 4, 8, 6))
        );
    }

    @Test
    void preferredLayoutSize_uses_wrapped_width_when_height_is_known() {
        var layout = new VerticalFlowLayout(VerticalFlowLayout.TOP, 5, 3);
        var container = panel(layout, 60, 20);
        container.add(child(10, 4));
        container.add(child(12, 5));
        container.add(child(8, 6));

        assertAll(
                () -> assertThat(layout.preferredLayoutSize(container)).isEqualTo(new Dimension(31, 14)),
                () -> assertThat(layout.minimumLayoutSize(container)).isEqualTo(new Dimension(31, 14))
        );
    }

    @Test
    void preferredLayoutSize_falls_back_to_single_column_when_height_is_not_positive() {
        var layout = new VerticalFlowLayout(VerticalFlowLayout.TOP, 5, 3);
        var container = panel(layout, 60, 0);
        container.add(child(10, 4));
        container.add(child(12, 5));
        container.add(child(8, 6));

        assertThat(layout.preferredLayoutSize(container)).isEqualTo(new Dimension(28, 28));
    }

    private static JPanel panel(VerticalFlowLayout layout, int width, int height) {
        var panel = new JPanel(layout);
        panel.setBorder(new EmptyBorder(1, 2, 3, 4));
        panel.setSize(width, height);
        return panel;
    }

    private static JPanel child(int preferredWidth, int preferredHeight) {
        var panel = new JPanel();
        panel.setPreferredSize(new Dimension(preferredWidth, preferredHeight));
        return panel;
    }
}
