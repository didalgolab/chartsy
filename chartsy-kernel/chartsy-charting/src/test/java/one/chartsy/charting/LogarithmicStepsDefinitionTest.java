package one.chartsy.charting;

import java.text.NumberFormat;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class LogarithmicStepsDefinitionTest {

    @Test
    void previousStepAndSubStepsFollowTheCurrentLogarithmicInterval() {
        Chart chart = new Chart();
        chart.getXScale().setLogarithmic(10.0);
        LogarithmicStepsDefinition steps =
                (LogarithmicStepsDefinition) chart.getXScale().getStepsDefinition();

        assertAll(
                () -> assertThat(steps.previousStep(100.0)).isEqualTo(100.0),
                () -> assertThat(steps.previousStep(63.0)).isEqualTo(10.0),
                () -> assertThat(steps.previousSubStep(63.0)).isEqualTo(60.0),
                () -> assertThat(steps.incrementStep(10.0)).isEqualTo(100.0),
                () -> assertThat(steps.incrementSubStep(60.0)).isEqualTo(70.0)
        );
    }

    @Test
    void setNumberFormatRetainsTheFormatterAndInvalidatesTheOwningScale() {
        class TestStepsDefinition extends LogarithmicStepsDefinition {
            private boolean refreshed;

            @Override
            void refreshScale() {
                refreshed = true;
            }
        }

        TestStepsDefinition steps = new TestStepsDefinition();
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);

        steps.setNumberFormat(format);

        assertAll(
                () -> assertThat(steps.getNumberFormat()).isSameAs(format),
                () -> assertThat(steps.refreshed).isTrue()
        );
    }
}
