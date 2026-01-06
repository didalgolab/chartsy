/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.core.io.ResourcePaths;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.data.provider.file.SimpleCandleLineMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChartExporterTest {

    @Test
    void can_export_as_png(@TempDir Path tempDir) throws Exception {
        FlatFileFormat format = btcDailyFormat();
        Path archive = ResourcePaths.pathToResource("BTC_DAILY.zip");

        try (FlatFileDataProvider provider = new FlatFileDataProvider(format, archive)) {
            var symbol = SymbolIdentity.of("BTC_DAILY");
            var resource = SymbolResource.of(symbol, TimeFrame.Period.DAILY);

            Path output = tempDir.resolve("chart.png");
            ChartExporter.export(output, provider, resource);

            assertThat(Files.size(output)).isGreaterThan(0L);
        }
    }

    @Test
    void can_export_as_svg(@TempDir Path tempDir) throws Exception {
        FlatFileFormat format = btcDailyFormat();
        Path archive = ResourcePaths.pathToResource("BTC_DAILY.zip");

        try (FlatFileDataProvider provider = new FlatFileDataProvider(format, archive)) {
            var symbol = SymbolIdentity.of("BTC_DAILY");
            var resource = SymbolResource.of(symbol, TimeFrame.Period.DAILY);

            Path output = tempDir.resolve("chart.svg");
            ChartExporter.export(output, provider, resource);

            assertThat(Files.size(output)).isGreaterThan(0L);
            assertThat(Files.readString(output)).contains("<svg");
        }
    }

    private static FlatFileFormat btcDailyFormat() {
        return FlatFileFormat.builder()
                .skipFirstLines(1)
                .lineMapper(new SimpleCandleLineMapper.Type(
                        ',', List.of("DATE", "OPEN", "HIGH", "LOW", "CLOSE", "VOLUME"),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .build();
    }
}
