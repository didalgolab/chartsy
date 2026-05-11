package one.chartsy.charting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.Color;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ColorExTest {

    @ParameterizedTest(name = "[{index}] alpha={1} -> {2}")
    @MethodSource("inRangeAlphaCases")
    void setAlpha_gives_rgb_copy_with_rounded_alpha(Color source, float alpha, int expectedAlpha) {
        var actual = ColorEx.setAlpha(source, alpha);

        assertColorCopy(source, actual, expectedAlpha);
    }

    @ParameterizedTest(name = "[{index}] alpha={1} -> {2}")
    @MethodSource("outOfRangeAlphaCases")
    void setAlpha_gives_clamped_alpha_when_ratio_is_outside_bounds(Color source, float alpha, int expectedAlpha) {
        var actual = ColorEx.setAlpha(source, alpha);

        assertColorCopy(source, actual, expectedAlpha);
    }

    @Test
    void setAlpha_gives_distinct_copy_when_alpha_matches() {
        var source = new Color(11, 22, 33, 128);

        var actual = ColorEx.setAlpha(source, 128f / 255f);

        assertColorCopy(source, actual, 128);
    }

    @Test
    void setAlpha_gives_null_pointer_when_color_is_null() {
        var thrown = assertThrows(NullPointerException.class, () -> ColorEx.setAlpha(null, 0.5f));

        assertThat(thrown).hasMessage("color");
    }

    private static Stream<Arguments> inRangeAlphaCases() {
        return Stream.of(
                arguments(new Color(12, 34, 56, 200), 0.0f, 0),
                arguments(new Color(12, 34, 56, 78), 0.5f, 128),
                arguments(new Color(90, 45, 180, 1), 1.0f / 255.0f, 1),
                arguments(new Color(200, 120, 40, 0), 1.0f, 255)
        );
    }

    private static Stream<Arguments> outOfRangeAlphaCases() {
        return Stream.of(
                arguments(new Color(2, 4, 8, 64), -0.01f, 0),
                arguments(new Color(2, 4, 8, 64), Float.NEGATIVE_INFINITY, 0),
                arguments(new Color(20, 40, 80, 64), 1.01f, 255),
                arguments(new Color(20, 40, 80, 64), Float.POSITIVE_INFINITY, 255)
        );
    }

    private static void assertColorCopy(Color source, Color actual, int expectedAlpha) {
        var expected = new Color(source.getRed(), source.getGreen(), source.getBlue(), expectedAlpha);

        assertAll(
                () -> assertThat(actual).isNotSameAs(source),
                () -> assertThat(actual).isEqualTo(expected),
                () -> assertThat(actual.getRed()).isEqualTo(source.getRed()),
                () -> assertThat(actual.getGreen()).isEqualTo(source.getGreen()),
                () -> assertThat(actual.getBlue()).isEqualTo(source.getBlue()),
                () -> assertThat(actual.getAlpha()).isEqualTo(expectedAlpha)
        );
    }
}
