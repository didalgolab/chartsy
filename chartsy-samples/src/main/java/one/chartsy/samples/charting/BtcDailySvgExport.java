/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.charting;

import one.chartsy.SymbolIdentity;
import one.chartsy.TimeFrame;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.data.provider.file.SimpleCandleLineMapper;
import one.chartsy.core.io.ResourcePaths;
import one.chartsy.ui.chart.ChartTool;

import java.awt.Dimension;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

public final class BtcDailySvgExport {

    private BtcDailySvgExport() {
    }

    public static void main(String[] args) throws Exception {
        FlatFileFormat format = FlatFileFormat.builder()
                .skipFirstLines(1)
                .lineMapper(new SimpleCandleLineMapper.Type(
                        ',', List.of("DATE", "OPEN", "HIGH", "LOW", "CLOSE", "VOLUME"),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .build();

        Path archive = ResourcePaths.pathToResource("BTC_DAILY.zip");
        FlatFileDataProvider provider = new FlatFileDataProvider(format, archive);
        try {
            var symbol = SymbolIdentity.of("BTC_DAILY");
            var timeFrame = TimeFrame.Period.DAILY;
            var range = ChartTool.DataRange.last(200);
            var size = new Dimension(1536, 793);

            SvgExportConfig config = parseArgs(args);
            if (config.compare) {
                Path standardOutput = withSuffix(config.output, "-standard");
                Path aggressiveOutput = withSuffix(config.output, "-aggressive");
                ChartTool.renderChartToSvg(standardOutput, provider, symbol, timeFrame, range, size,
                        ChartTool.SvgMinifyMode.STANDARD);
                Path standardSvgz = writeSvgz(standardOutput);
                ChartTool.renderChartToSvg(aggressiveOutput, provider, symbol, timeFrame, range, size,
                        ChartTool.SvgMinifyMode.AGGRESSIVE);
                Path aggressiveSvgz = writeSvgz(aggressiveOutput);
                printComparison(standardOutput, aggressiveOutput, standardSvgz, aggressiveSvgz);
            } else {
                ChartTool.renderChartToSvg(config.output, provider, symbol, timeFrame, range, size, config.minifyMode);
                Path svgzOutput = writeSvgz(config.output);
                System.out.println("SVG chart saved to: " + config.output.toAbsolutePath());
                System.out.println("SVGZ chart saved to: " + svgzOutput.toAbsolutePath());
            }
        } finally {
            provider.close();
        }
    }

    private static SvgExportConfig parseArgs(String[] args) {
        Path output = Path.of("btc_daily_chart.svg");
        boolean compare = false;
        ChartTool.SvgMinifyMode minifyMode = ChartTool.SvgMinifyMode.AGGRESSIVE;
        for (String arg : args) {
            if ("--compare".equals(arg)) {
                compare = true;
            } else if ("--aggressive".equals(arg)) {
                minifyMode = ChartTool.SvgMinifyMode.AGGRESSIVE;
            } else if ("--standard".equals(arg)) {
                minifyMode = ChartTool.SvgMinifyMode.STANDARD;
            } else if (arg.startsWith("--minify=")) {
                String value = arg.substring("--minify=".length()).trim().toLowerCase(Locale.ROOT);
                if ("aggressive".equals(value)) {
                    minifyMode = ChartTool.SvgMinifyMode.AGGRESSIVE;
                } else if ("standard".equals(value)) {
                    minifyMode = ChartTool.SvgMinifyMode.STANDARD;
                }
            } else if (!arg.startsWith("-")) {
                output = Path.of(arg);
            }
        }
        output = normalizeSvgOutput(output);
        return new SvgExportConfig(output, compare, minifyMode);
    }

    private static Path withSuffix(Path output, String suffix) {
        String name = output.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        String updated = base + suffix + ext;
        return output.resolveSibling(updated);
    }

    private static Path normalizeSvgOutput(Path output) {
        String name = output.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return output.resolveSibling(name + ".svg");
        }
        String ext = name.substring(dot).toLowerCase(Locale.ROOT);
        if (".svgz".equals(ext)) {
            String base = name.substring(0, dot);
            return output.resolveSibling(base + ".svg");
        }
        return output;
    }

    private static Path writeSvgz(Path svgOutput) throws Exception {
        Path svgzOutput = replaceExtension(svgOutput, ".svgz");
        try (var input = Files.newInputStream(svgOutput);
             var output = new BestCompressionGzipOutputStream(Files.newOutputStream(svgzOutput))) {
            input.transferTo(output);
        }
        return svgzOutput;
    }

    private static Path replaceExtension(Path output, String extension) {
        String name = output.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String updated = base + extension;
        return output.resolveSibling(updated);
    }

    private static void printComparison(Path standardOutput,
                                        Path aggressiveOutput,
                                        Path standardSvgz,
                                        Path aggressiveSvgz) throws Exception {
        long standardSize = Files.size(standardOutput);
        long aggressiveSize = Files.size(aggressiveOutput);
        long delta = standardSize - aggressiveSize;
        double percent = standardSize > 0 ? (delta * 100.0 / standardSize) : 0.0;
        long standardSvgzSize = Files.size(standardSvgz);
        long aggressiveSvgzSize = Files.size(aggressiveSvgz);
        long svgzDelta = standardSvgzSize - aggressiveSvgzSize;
        double svgzPercent = standardSvgzSize > 0 ? (svgzDelta * 100.0 / standardSvgzSize) : 0.0;
        System.out.println("Standard SVG saved to: " + standardOutput.toAbsolutePath());
        System.out.println("Aggressive SVG saved to: " + aggressiveOutput.toAbsolutePath());
        System.out.println("Standard SVGZ saved to: " + standardSvgz.toAbsolutePath());
        System.out.println("Aggressive SVGZ saved to: " + aggressiveSvgz.toAbsolutePath());
        System.out.println("Standard size: " + standardSize + " bytes");
        System.out.println("Aggressive size: " + aggressiveSize + " bytes");
        System.out.println("Savings: " + delta + " bytes (" + String.format(Locale.ROOT, "%.1f", percent) + "%)");
        System.out.println("Standard SVGZ size: " + standardSvgzSize + " bytes");
        System.out.println("Aggressive SVGZ size: " + aggressiveSvgzSize + " bytes");
        System.out.println("SVGZ savings: " + svgzDelta + " bytes (" + String.format(Locale.ROOT, "%.1f", svgzPercent) + "%)");
    }

    private record SvgExportConfig(Path output, boolean compare, ChartTool.SvgMinifyMode minifyMode) {
    }

    private static final class BestCompressionGzipOutputStream extends GZIPOutputStream {
        private BestCompressionGzipOutputStream(OutputStream out) throws java.io.IOException {
            super(new BufferedOutputStream(out));
            def.setLevel(Deflater.BEST_COMPRESSION);
        }
    }
}
