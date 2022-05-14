/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.file;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.CandleSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SymbolResourceFilesTest {

    final SymbolResource<Candle> EURUSD_M1 = SymbolResource.of("EURUSD", TimeFrame.Period.M1);

    @ParameterizedTest
    @MethodSource("files")
    void newCandleSeries_can_load_CandleSeries_from_files(Path file) throws IOException {
        CandleSeries series = SymbolResourceFiles.newCandleSeries(EURUSD_M1, file);

        assertEquals(91, series.length());
        assertEquals(totalCandle(), CandleSupport.merge(series));
    }

    private static Stream<Path> files() {
        return Stream.of(
                Path.of("src/test/resources/one/chartsy/data/file/SymbolResourceFilesTest.jsons"),
                Path.of("src/test/resources/one/chartsy/data/file/SymbolResourceFilesTest.jsons.gz")
        );
    }

    private static Candle totalCandle() throws IOException {
        var p = Path.of("src/test/resources/one/chartsy/data/file/SymbolResourceFilesTest.json");
        return SymbolResourceFiles.newCandle(p);
    }
}