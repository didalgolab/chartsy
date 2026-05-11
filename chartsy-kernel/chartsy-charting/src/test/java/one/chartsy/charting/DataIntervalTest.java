package one.chartsy.charting;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class DataIntervalTest {

    @Test
    void empty_uses_the_canonical_sentinel() {
        var created = new DataInterval();
        var reset = new DataInterval(-5.0, 8.0);

        reset.empty();

        assertAll(
                () -> assertThat(created.isEmpty()).isTrue(),
                () -> assertThat(created.getMin()).isEqualTo(1.0),
                () -> assertThat(created.getMax()).isEqualTo(-1.0),
                () -> assertThat(reset.isEmpty()).isTrue(),
                () -> assertThat(reset.getMin()).isEqualTo(1.0),
                () -> assertThat(reset.getMax()).isEqualTo(-1.0)
        );
    }

    @Test
    void disjoint_intersection_becomes_canonical_empty() {
        var interval = new DataInterval(10.0, 20.0);

        interval.intersection(new DataInterval(30.0, 40.0));

        assertAll(
                () -> assertThat(interval.isEmpty()).isTrue(),
                () -> assertThat(interval.getMin()).isEqualTo(1.0),
                () -> assertThat(interval.getMax()).isEqualTo(-1.0)
        );
    }

    @Test
    void subtract_trims_only_outer_overlap() {
        var leftTrimmed = new DataInterval(0.0, 10.0);
        var rightTrimmed = new DataInterval(0.0, 10.0);
        var splitAttempt = new DataInterval(0.0, 10.0);

        leftTrimmed.subtract(new DataInterval(-2.0, 4.0));
        rightTrimmed.subtract(new DataInterval(6.0, 12.0));
        splitAttempt.subtract(new DataInterval(4.0, 6.0));

        assertAll(
                () -> assertThat(leftTrimmed).isEqualTo(new DataInterval(4.0, 10.0)),
                () -> assertThat(rightTrimmed).isEqualTo(new DataInterval(0.0, 6.0)),
                () -> assertThat(splitAttempt).isEqualTo(new DataInterval(0.0, 10.0))
        );
    }

    @Test
    void before_and_after_helpers_compare_probe_values_to_interval_edges() {
        var interval = new DataInterval(10.0, 20.0);

        assertAll(
                () -> assertThat(interval.isBefore(10.0)).isTrue(),
                () -> assertThat(interval.isBefore(10.1)).isFalse(),
                () -> assertThat(interval.isStrictlyBefore(9.9)).isTrue(),
                () -> assertThat(interval.isStrictlyBefore(10.0)).isFalse(),
                () -> assertThat(interval.isAfter(20.0)).isTrue(),
                () -> assertThat(interval.isAfter(19.9)).isFalse(),
                () -> assertThat(interval.isStrictlyAfter(20.1)).isTrue(),
                () -> assertThat(interval.isStrictlyAfter(20.0)).isFalse()
        );
    }
}
