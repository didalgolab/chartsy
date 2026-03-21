package one.chartsy.charting;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogarithmicAxisTransformerTest {

    @Test
    void disablingPowerRoundingKeepsTheVisibleRangeAtItsActualBounds() {
        LogarithmicAxisTransformer transformer = new LogarithmicAxisTransformer();
        transformer.setRoundingToPowers(false);
        DataInterval interval = new DataInterval(100.0, 140.0);

        boolean changed = transformer.validateInterval(interval);

        assertThat(changed).isFalse();
        assertThat(interval.getMin()).isEqualTo(100.0);
        assertThat(interval.getMax()).isEqualTo(140.0);
    }
}
