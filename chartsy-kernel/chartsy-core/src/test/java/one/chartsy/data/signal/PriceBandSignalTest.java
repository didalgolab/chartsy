package one.chartsy.data.signal;

import one.chartsy.Candle;
import one.chartsy.time.Chronological;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleConsumer;

import static org.assertj.core.api.Assertions.*;

class PriceBandSignalTest {

    private static Candle createCandle(double open, double high, double low, double close) {
        return Candle.of(Chronological.now(), open, high, low, close, 1);
    }

    @Nested
    class BuilderTests {

        @Test
        void build_creates_instance_with_default_settings() {
            var signal = PriceBandSignal.create();
            assertThat(signal).isNotNull();
            assertThat(signal.isArmed()).isFalse();
            assertThat(signal.getLowerBand()).isEqualTo(Double.NEGATIVE_INFINITY);
            assertThat(signal.getUpperBand()).isEqualTo(Double.POSITIVE_INFINITY);
            // Internal fields check (not strictly necessary but confirms defaults)
            assertThat(signal)
                    .extracting("lowerBandAction", "upperBandAction", "disarmOnFirstTrigger")
                    .containsExactly(PriceBandSignal.NO_ACTION, PriceBandSignal.NO_ACTION, false);
        }

        @Test
        void builder_throws_NullPointerException_if_actions_are_null() {
            assertThatNullPointerException()
                    .isThrownBy(() -> PriceBandSignal.builder().lowerBandAction(null))
                    .withMessageContaining("lowerBandAction");

            assertThatNullPointerException()
                    .isThrownBy(() -> PriceBandSignal.builder().upperBandAction(null))
                    .withMessageContaining("upperBandAction");
        }

        @Test
        void builder_configures_custom_actions_correctly() {
            DoubleConsumer mockLowerAction = p -> {};
            DoubleConsumer mockUpperAction = p -> {};
            var signal = PriceBandSignal.builder()
                    .lowerBandAction(mockLowerAction)
                    .upperBandAction(mockUpperAction)
                    .build();

            assertThat(signal)
                    .extracting("lowerBandAction", "upperBandAction")
                    .containsExactly(mockLowerAction, mockUpperAction);
        }

        @Test
        @DisplayName("Sets triggerOnce correctly")
        void builder_configures_triggerOnce_correctly() {
            var signal = PriceBandSignal.builder().triggerOnce().build();
            assertThat(signal).extracting("disarmOnFirstTrigger").isEqualTo(true);
        }

        @Test
        @DisplayName("Sets triggerIndependently correctly (explicitly)")
        void builder_configures_triggerIndependently_correctly() {
            // Start with triggerOnce, then override
            var signal = PriceBandSignal.builder()
                    .triggerOnce()
                    .triggerIndependently()
                    .build();
            assertThat(signal).extracting("disarmOnFirstTrigger").isEqualTo(false);
        }
    }

    @Nested
    class ArmingTests {
        final PriceBandSignal signal = PriceBandSignal.create();

        @Test
        void gives_initial_state_disarmed_and_with_infinite_bands() {
            assertThat(signal.isArmed()).isFalse();
            assertThat(signal.isLowerBandTriggered()).isFalse();
            assertThat(signal.isUpperBandTriggered()).isFalse();
            assertThat(signal.getLowerBand()).isEqualTo(Double.NEGATIVE_INFINITY);
            assertThat(signal.getUpperBand()).isEqualTo(Double.POSITIVE_INFINITY);
        }

        @Test
        void arm_sets_bands_arms_and_resets_rriggers() {
            // Set some initial triggered state (though it shouldn't matter)
            // We need to arm, trigger, then re-arm to test reset properly
            signal.arm(94, 106);
            signal.poll(createCandle(115, 115, 115, 115)); // Trigger upper
            signal.poll(createCandle(85, 85, 85, 85));    // Trigger lower (if independent)
            assertThat(signal.isUpperBandTriggered()).isTrue();
            assertThat(signal.isLowerBandTriggered()).isTrue();

            // Now, re-arm
            signal.arm(95, 105);

            assertThat(signal.isArmed()).isTrue();
            assertThat(signal.getLowerBand()).isEqualTo(95.0);
            assertThat(signal.getUpperBand()).isEqualTo(105.0);
            assertThat(signal.isLowerBandTriggered()).isFalse(); // Reset check
            assertThat(signal.isUpperBandTriggered()).isFalse(); // Reset check
        }

        @Test
        void arm_throws_IllegalArgumentException_when_upper_le_Lower() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> signal.arm(100, 100));

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> signal.arm(101, 100));
        }

        @Test
        void armAroundEntry_calculates_bands_and_arms() {
            signal.armAroundEntry(100, 5, 10); // SL 5 below, TP 10 above

            assertThat(signal.isArmed()).isTrue();
            assertThat(signal.getLowerBand()).isEqualTo(95.0); // 100 - 5
            assertThat(signal.getUpperBand()).isEqualTo(110.0); // 100 + 10
            assertThat(signal.isLowerBandTriggered()).isFalse();
            assertThat(signal.isUpperBandTriggered()).isFalse();
        }

        @ParameterizedTest
        @CsvSource({
                "0, 10",
                "10, 0",
                "-5, 10",
                "10, -5" })
        void armAroundEntry_throws_IllegalArgumentException_when_non_positive_distance(double lowerDist, double upperDist) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> signal.armAroundEntry(100, lowerDist, upperDist));
        }
    }

    @Nested
    class DisarmAndResetTests {
        final AtomicBoolean lowerActionCalled = new AtomicBoolean(false);
        final AtomicBoolean upperActionCalled = new AtomicBoolean(false);
        final PriceBandSignal signal = PriceBandSignal.builder()
                .lowerBandAction(p -> lowerActionCalled.set(true))
                .upperBandAction(p -> upperActionCalled.set(true))
                .build();

        @BeforeEach
        void setUp() {
            signal.arm(90, 110);
        }

        @Test
        void disarm_sets_armed_to_false_and_leaves_triggers_unchanged() {
            signal.poll(createCandle(115, 115, 85, 100)); // Trigger both
            assertThat(signal.isUpperBandTriggered()).isTrue();
            assertThat(signal.isLowerBandTriggered()).isTrue();
            assertThat(signal.isArmed()).isTrue(); // Still armed (independent mode)

            signal.disarm();

            assertThat(signal.isArmed()).isFalse();
            assertThat(signal.isUpperBandTriggered()).isTrue(); // Flag remains
            assertThat(signal.isLowerBandTriggered()).isTrue(); // Flag remains
        }

        @Test
        void poll_does_nothing_when_disarmed() {
            signal.disarm();
            assertThat(signal.isArmed()).isFalse();

            boolean triggeredThisPoll = signal.poll(createCandle(115, 115, 85, 100));

            assertThat(triggeredThisPoll).isFalse();
            assertThat(lowerActionCalled).isFalse();
            assertThat(upperActionCalled).isFalse();
            assertThat(signal.isLowerBandTriggered()).isFalse();
            assertThat(signal.isUpperBandTriggered()).isFalse();
        }

        @Test
        void reset_clears_triggers_and_leaves_armed_unchanged() {
            signal.poll(createCandle(115, 115, 85, 100)); // Trigger both
            assertThat(signal.isUpperBandTriggered()).isTrue();
            assertThat(signal.isLowerBandTriggered()).isTrue();
            assertThat(signal.isArmed()).isTrue();

            signal.reset();

            assertThat(signal.isArmed()).isTrue(); // Still armed
            assertThat(signal.isUpperBandTriggered()).isFalse(); // Reset
            assertThat(signal.isLowerBandTriggered()).isFalse(); // Reset

            // Should be able to trigger again
            boolean triggeredAgain = signal.poll(createCandle(112, 112, 111, 111)); // Trigger upper again
            assertThat(triggeredAgain).isTrue();
            assertThat(upperActionCalled).isTrue();
            assertThat(signal.isUpperBandTriggered()).isTrue();
            assertThat(signal.isLowerBandTriggered()).isFalse(); // Lower not hit this time
        }

        @Test
        void reset_clears_flags_and_remains_disarmed_when_disarmed() {
            signal.poll(createCandle(115, 115, 85, 100)); // Trigger both
            signal.disarm();
            assertThat(signal.isArmed()).isFalse();
            assertThat(signal.isUpperBandTriggered()).isTrue();
            assertThat(signal.isLowerBandTriggered()).isTrue();

            signal.reset();

            assertThat(signal.isArmed()).isFalse(); // Still disarmed
            assertThat(signal.isUpperBandTriggered()).isFalse(); // Reset
            assertThat(signal.isLowerBandTriggered()).isFalse(); // Reset
        }
    }

    @Nested
    class PollingIndependentTests {
        final AtomicReference<Double> lowerTriggerPrice = new AtomicReference<>();
        final AtomicReference<Double> upperTriggerPrice = new AtomicReference<>();
        final PriceBandSignal signal = PriceBandSignal.builder()
                .lowerBandAction(lowerTriggerPrice::set)
                .upperBandAction(upperTriggerPrice::set)
                .triggerIndependently() // Explicit default
                .build();

        @BeforeEach
        void setUp() {
            signal.arm(90, 110);
        }

        @Test
        void poll_gives_false_and_does_nothing_if_price_within_bands() {
            boolean triggered = signal.poll(createCandle(100, 105, 95, 100));

            assertThat(triggered).isFalse();
            assertThat(lowerTriggerPrice.get()).isNull();
            assertThat(upperTriggerPrice.get()).isNull();
            assertThat(signal.isLowerBandTriggered()).isFalse();
            assertThat(signal.isUpperBandTriggered()).isFalse();
            assertThat(signal.isArmed()).isTrue();
        }

        @Test
        void poll_triggers_upper_action_when_high_hits_upper_band() {
            boolean triggered = signal.poll(createCandle(105, 110, 104, 109)); // High touches band

            assertThat(triggered).isTrue();
            assertThat(upperTriggerPrice.get()).isEqualTo(110.0); // Max(band, open) = Max(110, 105)
            assertThat(lowerTriggerPrice.get()).isNull();
            assertThat(signal.isUpperBandTriggered()).isTrue();
            assertThat(signal.isLowerBandTriggered()).isFalse();
            assertThat(signal.isArmed()).isTrue();
        }

        @Test
        void poll_triggers_lower_action_when_low_hits_lower_band() {
            boolean triggered = signal.poll(createCandle(95, 96, 90, 91)); // Low touches band

            assertThat(triggered).isTrue();
            assertThat(lowerTriggerPrice.get()).isEqualTo(90.0); // Min(band, open) = Min(90, 95)
            assertThat(upperTriggerPrice.get()).isNull();
            assertThat(signal.isLowerBandTriggered()).isTrue();
            assertThat(signal.isUpperBandTriggered()).isFalse();
            assertThat(signal.isArmed()).isTrue();
        }

        @Test
        void poll_triggers_upper_with_open_price_when_open_gaps_above_band() {
            boolean triggered = signal.poll(createCandle(112, 115, 111, 113)); // Open > 110

            assertThat(triggered).isTrue();
            assertThat(upperTriggerPrice.get()).isEqualTo(112.0); // Max(band, open) = Max(110, 112)
            assertThat(lowerTriggerPrice.get()).isNull();
            assertThat(signal.isUpperBandTriggered()).isTrue();
            assertThat(signal.isLowerBandTriggered()).isFalse();
        }

        @Test
        void poll_triggers_lower_when_open_price_gaps_below_band() {
            boolean triggered = signal.poll(createCandle(88, 89, 85, 87)); // Open < 90

            assertThat(triggered).isTrue();
            assertThat(lowerTriggerPrice.get()).isEqualTo(88.0); // Min(band, open) = Min(90, 88)
            assertThat(upperTriggerPrice.get()).isNull();
            assertThat(signal.isLowerBandTriggered()).isTrue();
            assertThat(signal.isUpperBandTriggered()).isFalse();
        }

        @Test
        void poll_triggers_only_once_per_band() {
            // First trigger
            signal.poll(createCandle(111, 111, 105, 108));
            assertThat(upperTriggerPrice.get()).isEqualTo(111.0);
            assertThat(signal.isUpperBandTriggered()).isTrue();
            upperTriggerPrice.set(null); // Reset capture

            // Second poll hitting upper band again
            boolean triggeredAgain = signal.poll(createCandle(112, 112, 110, 111));
            assertThat(triggeredAgain).isFalse(); // Should not trigger *again*
            assertThat(upperTriggerPrice.get()).isNull(); // Action not called again
            assertThat(signal.isUpperBandTriggered()).isTrue(); // Flag remains true

            // Lower band can still trigger
            boolean lowerTriggered = signal.poll(createCandle(89, 95, 89, 92));
            assertThat(lowerTriggered).isTrue();
            assertThat(lowerTriggerPrice.get()).isEqualTo(89.0);
            assertThat(signal.isLowerBandTriggered()).isTrue();
        }

        @Test
        void poll_triggers_both_actions_when_price_hits_both_bands() {
            boolean triggered = signal.poll(createCandle(100, 115, 85, 100)); // High >= 110, Low <= 90

            assertThat(triggered).isTrue(); // poll returns true if *any* action triggered
            assertThat(upperTriggerPrice.get()).isEqualTo(110.0); // Max(110, 100)
            assertThat(lowerTriggerPrice.get()).isEqualTo(90.0);  // Min(90, 100)
            assertThat(signal.isUpperBandTriggered()).isTrue();
            assertThat(signal.isLowerBandTriggered()).isTrue();
            assertThat(signal.isArmed()).isTrue(); // Stays armed
        }

        @Test
        void poll_still_updates_states_when_action_throws_Exception() {
            var signal = PriceBandSignal.builder()
                    .upperBandAction(p -> { throw new RuntimeException("Action failed!"); })
                    .lowerBandAction(lowerTriggerPrice::set) // Normal lower action
                    .build();
            signal.arm(90, 110);

            // Trigger upper band (which will throw)
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> signal.poll(createCandle(111, 111, 105, 108)))
                    .withMessage("Action failed!");

            // Verify state updated despite exception
            assertThat(signal.isUpperBandTriggered()).isTrue();
            assertThat(signal.isArmed()).isTrue(); // Still armed (independent mode)
            assertThat(upperTriggerPrice.get()).isNull(); // Action failed before setting
            assertThat(lowerTriggerPrice.get()).isNull();
            assertThat(signal.isLowerBandTriggered()).isFalse();

            // Check subsequent poll doesn't re-trigger upper, but can trigger lower
            boolean triggeredLower = signal.poll(createCandle(88, 95, 88, 90));
            assertThat(triggeredLower).isTrue();
            assertThat(lowerTriggerPrice.get()).isEqualTo(88.0);
            assertThat(signal.isLowerBandTriggered()).isTrue();
            assertThat(signal.isUpperBandTriggered()).isTrue(); // Still true from before
        }

        @Test
        void poll_throws_NullPointerException_on_NULL_Candle() {
            assertThatNullPointerException()
                    .isThrownBy(() -> signal.poll(null));
        }
    }

    @Nested
    class PollingTriggerOnceTests {
        final AtomicReference<Double> lowerTriggerPrice = new AtomicReference<>();
        final AtomicReference<Double> upperTriggerPrice = new AtomicReference<>();
        PriceBandSignal signal;

        @BeforeEach
        void setUp() {
            signal = PriceBandSignal.builder()
                    .lowerBandAction(lowerTriggerPrice::set)
                    .upperBandAction(upperTriggerPrice::set)
                    .triggerOnce()
                    .build();
            signal.arm(90, 110);
        }

        @Test
        void poll_triggers_upper_and_disarms_when_upper_hit() {
            boolean triggered = signal.poll(createCandle(105, 110, 104, 109));

            assertThat(triggered).isTrue();
            assertThat(upperTriggerPrice.get()).isEqualTo(110.0);
            assertThat(lowerTriggerPrice.get()).isNull();
            assertThat(signal.isUpperBandTriggered()).isTrue();
            assertThat(signal.isLowerBandTriggered()).isFalse();
            assertThat(signal.isArmed()).isFalse(); // Disarmed
        }

        @Test
        void poll_triggers_lower_and_disarms_when_lower_hit() {
            boolean triggered = signal.poll(createCandle(95, 96, 90, 91));

            assertThat(triggered).isTrue();
            assertThat(lowerTriggerPrice.get()).isEqualTo(90.0);
            assertThat(upperTriggerPrice.get()).isNull();
            assertThat(signal.isLowerBandTriggered()).isTrue();
            assertThat(signal.isUpperBandTriggered()).isFalse();
            assertThat(signal.isArmed()).isFalse(); // Disarmed
        }

        @Test
        void poll_does_nothing_after_first_hit() {
            // First hit (upper)
            signal.poll(createCandle(111, 111, 105, 108));
            assertThat(signal.isArmed()).isFalse();
            upperTriggerPrice.set(null); // Reset capture for clarity

            // Second poll (hitting lower now)
            boolean triggeredAgain = signal.poll(createCandle(88, 95, 88, 90));

            assertThat(triggeredAgain).isFalse(); // Not triggered because already disarmed
            assertThat(lowerTriggerPrice.get()).isNull(); // Lower action not called
            assertThat(upperTriggerPrice.get()).isNull(); // Upper action not called again
            assertThat(signal.isLowerBandTriggered()).isFalse(); // Lower was never triggered
            assertThat(signal.isUpperBandTriggered()).isTrue(); // Upper flag remains
        }

        @Test
        void poll_triggers_only_upper_and_disarms_when_hit_both() {
            boolean triggered = signal.poll(createCandle(100, 115, 85, 100)); // Hits both

            assertThat(triggered).isTrue();
            assertThat(upperTriggerPrice.get()).isEqualTo(110.0); // Upper triggered
            assertThat(lowerTriggerPrice.get()).isNull();         // Lower NOT triggered
            assertThat(signal.isUpperBandTriggered()).isTrue();
            assertThat(signal.isLowerBandTriggered()).isFalse(); // Lower check skipped
            assertThat(signal.isArmed()).isFalse(); // Disarmed by upper trigger
        }

        @Test
        void poll_updates_state_and_disarms_when_action_throws_Exception() {
            signal = PriceBandSignal.builder()
                    .upperBandAction(p -> { throw new RuntimeException("Action failed!"); })
                    .triggerOnce()
                    .build();
            signal.arm(90, 110);

            // Trigger upper band (which will throw)
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> signal.poll(createCandle(111, 111, 105, 108)))
                    .withMessage("Action failed!");

            // Verify state updated despite exception
            assertThat(signal.isUpperBandTriggered()).isTrue();
            assertThat(signal.isArmed()).isFalse(); // Disarmed
            assertThat(signal.isLowerBandTriggered()).isFalse();
        }
    }
}