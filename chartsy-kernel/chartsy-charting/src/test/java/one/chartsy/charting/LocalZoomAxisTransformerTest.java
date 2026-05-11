package one.chartsy.charting;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class LocalZoomAxisTransformerTest {

    @Test
    void discontinuous_mapping_collapses_transition_bands_onto_zoom_edges() throws AxisTransformerException {
        var transformer = LocalZoomAxisTransformer.create(axisWithVisibleRange(0.0, 100.0), 40.0, 60.0, 2.0, false);

        assertAll(
                () -> assertThat(transformer.apply(20.0)).isEqualTo(20.0),
                () -> assertThat(transformer.apply(35.0)).isEqualTo(30.0),
                () -> assertThat(transformer.apply(50.0)).isEqualTo(50.0),
                () -> assertThat(transformer.apply(65.0)).isEqualTo(70.0),
                () -> assertThat(transformer.inverse(30.0)).isEqualTo(40.0),
                () -> assertThat(transformer.inverse(70.0)).isEqualTo(60.0)
        );
    }

    @Test
    void continuous_mapping_recompresses_outer_segments_and_batch_apply_matches_scalar_behavior()
            throws AxisTransformerException {
        var transformer = LocalZoomAxisTransformer.create(axisWithVisibleRange(0.0, 100.0), 40.0, 60.0, 2.0, true);
        var values = new double[]{20.0, 50.0, 80.0};
        var expected = new double[]{
                transformer.apply(20.0),
                transformer.apply(50.0),
                transformer.apply(80.0)
        };

        assertAll(
                () -> assertThat(expected).containsExactly(15.0, 50.0, 85.0),
                () -> assertThat(transformer.inverse(15.0)).isEqualTo(20.0),
                () -> assertThat(transformer.inverse(85.0)).isEqualTo(80.0),
                () -> assertThat(transformer.apply(values, values.length)).containsExactly(expected)
        );
    }

    @Test
    void setZoomRange_rejects_ranges_that_leave_the_visible_interval() {
        var transformer = LocalZoomAxisTransformer.create(axisWithVisibleRange(0.0, 100.0), 40.0, 60.0, 2.0, false);

        boolean changed = transformer.setZoomRange(-5.0, 20.0);

        assertAll(
                () -> assertThat(changed).isFalse(),
                () -> assertThat(transformer.getZoomRange()).isEqualTo(new DataInterval(40.0, 60.0))
        );
    }

    @Test
    void validateInterval_expands_ranges_to_keep_the_zoom_window_visible() {
        var transformer = LocalZoomAxisTransformer.create(null, 40.0, 60.0, 2.0, false);
        var interval = new DataInterval(45.0, 55.0);

        boolean changed = transformer.validateInterval(interval);

        assertAll(
                () -> assertThat(changed).isTrue(),
                () -> assertThat(interval).isEqualTo(new DataInterval(40.0, 60.0))
        );
    }

    private static Axis axisWithVisibleRange(double min, double max) {
        var axis = new Axis(Axis.X_AXIS);
        axis.setDataRange(min, max);
        axis.setVisibleRange(min, max);
        return axis;
    }
}
