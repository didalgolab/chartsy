/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ChartFontsTest {

    @Test
    void defaultChartFontLoads() {
        assertThatCode(ChartFonts::defaultChartFont).doesNotThrowAnyException();
        assertThat(ChartFonts.defaultChartFont()).isNotNull();
    }

    @Test
    void embeddedRegularFontResourceIsValid() throws Exception {
        assertThat(loadFont("/one/chartsy/ui/chart/fonts/IosevkaTerm-Regular.ttf").getPSName())
                .isEqualTo("Iosevka-Term");
    }

    @Test
    void embeddedBoldFontResourceIsValid() throws Exception {
        assertThat(loadFont("/one/chartsy/ui/chart/fonts/IosevkaTerm-Bold.ttf").getPSName())
                .isEqualTo("Iosevka-Term-Bold");
    }

    private Font loadFont(String resourcePath) throws Exception {
        try (InputStream input = ChartFonts.class.getResourceAsStream(resourcePath)) {
            assertThat(input).isNotNull();
            return Font.createFont(Font.TRUETYPE_FONT, input);
        }
    }
}
