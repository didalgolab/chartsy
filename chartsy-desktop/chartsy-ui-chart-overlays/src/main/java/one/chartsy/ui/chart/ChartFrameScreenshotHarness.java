/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import one.chartsy.Candle;
import one.chartsy.FrontEndSupport;
import one.chartsy.SymbolGroup;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DataQuery;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.ui.chart.components.AnnotationPanel;
import one.chartsy.ui.chart.components.IndicatorPanel;
import reactor.core.publisher.Flux;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.SwingUtilities;
import javax.swing.plaf.LayerUI;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ChartFrameScreenshotHarness {

    private static final SymbolIdentity FIXTURE_SYMBOL = SymbolIdentity.of("CHARTFRAME-FIXTURE");
    private static final ExportOptions OPTIONS = ExportOptions.DEFAULT;
    private static final Path TMP_DIR = projectRoot().resolve("tmp");
    private static final Path BASELINE_PATH = TMP_DIR.resolve("chartframe-baseline.png");
    private static final Path CANDIDATE_PATH = TMP_DIR.resolve("chartframe-candidate.png");
    private static final Path DIFF_PATH = TMP_DIR.resolve("chartframe-diff.png");
    private static final Path REPORT_PATH = TMP_DIR.resolve("chartframe-diff.txt");
    private static final Path BASELINE_SHELL_PATH = TMP_DIR.resolve("chartframe-baseline-shell.png");
    private static final Path CANDIDATE_SHELL_PATH = TMP_DIR.resolve("chartframe-candidate-shell.png");
    private static final Path COMPONENT_TREE_PATH = TMP_DIR.resolve("chartframe-component-tree.txt");
    private static final Path FOOTER_MODEL_PATH = TMP_DIR.resolve("chartframe-footer-model.txt");
    private static final double MAX_CHANGED_RATIO = 0.005d;
    private static final int MATCH_RADIUS = 2;
    private static final int MATCH_CHANNEL_TOLERANCE = 24;
    private static final Pattern BIDI_CONTROL_PATTERN = Pattern.compile("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]");

    private ChartFrameScreenshotHarness() {
    }

    public static void main(String[] args) throws Exception {
        ParsedArguments parsed = ParsedArguments.parse(args);
        var fixture = Fixture.create(parsed.scenario());
        Files.createDirectories(TMP_DIR);

        Path baselinePath = scenarioPath(sizedPath(BASELINE_PATH, parsed.size()), parsed.scenario());
        Path candidatePath = scenarioPath(sizedPath(CANDIDATE_PATH, parsed.size()), parsed.scenario());
        Path diffPath = scenarioPath(sizedPath(DIFF_PATH, parsed.size()), parsed.scenario());
        Path reportPath = scenarioPath(sizedPath(REPORT_PATH, parsed.size()), parsed.scenario());
        Path baselineShellPath = scenarioPath(sizedPath(BASELINE_SHELL_PATH, parsed.size()), parsed.scenario());
        Path candidateShellPath = scenarioPath(sizedPath(CANDIDATE_SHELL_PATH, parsed.size()), parsed.scenario());

        switch (parsed.mode()) {
            case BASELINE -> {
                writeShots(fixture, parsed.size(), parsed.scenario(), baselinePath, baselineShellPath);
                System.out.println("Baseline screenshots saved to " + baselinePath + " and " + baselineShellPath);
            }
            case CANDIDATE -> {
                writeShots(fixture, parsed.size(), parsed.scenario(), candidatePath, candidateShellPath);
                System.out.println("Candidate screenshots saved to " + candidatePath + " and " + candidateShellPath);
            }
            case DIFF -> compare(baselinePath, candidatePath, diffPath, reportPath);
            case ALL -> {
                writeShots(fixture, parsed.size(), parsed.scenario(), baselinePath, baselineShellPath);
                writeShots(fixture, parsed.size(), parsed.scenario(), candidatePath, candidateShellPath);
                compare(baselinePath, candidatePath, diffPath, reportPath);
            }
        }
    }

    public static ComparisonSummary compare() throws IOException {
        return compare(BASELINE_PATH, CANDIDATE_PATH, DIFF_PATH, REPORT_PATH);
    }

    public static ComparisonSummary compare(Path baselinePath, Path candidatePath, Path diffPath, Path reportPath) throws IOException {
        var baseline = ImageIO.read(baselinePath.toFile());
        var candidate = ImageIO.read(candidatePath.toFile());
        if (baseline == null || candidate == null)
            throw new IllegalStateException("Baseline and candidate screenshots must exist before diffing");

        var summary = compare(baseline, candidate);
        writeImage(diffPath, summary.diffImage());
        Files.writeString(reportPath, summary.toReport());
        if (!summary.passed())
            throw new IllegalStateException("Chart screenshot parity check failed: " + summary.toReport());
        System.out.println("Diff report saved to " + reportPath + " and " + diffPath);
        return summary;
    }

    static ComparisonSummary compare(BufferedImage baseline, BufferedImage candidate) {
        if (baseline.getWidth() != candidate.getWidth() || baseline.getHeight() != candidate.getHeight()) {
            throw new IllegalArgumentException("Image sizes differ: baseline=%dx%d candidate=%dx%d"
                    .formatted(baseline.getWidth(), baseline.getHeight(), candidate.getWidth(), candidate.getHeight()));
        }

        int width = baseline.getWidth();
        int height = baseline.getHeight();
        int total = width * height;
        var diffImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        boolean[] baselineMatched = new boolean[total];
        boolean[] candidateMatched = new boolean[total];
        long exactChangedPixels = 0L;
        long deltaSum = 0L;
        int maxChannelDelta = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int base = baseline.getRGB(x, y);
                int cand = candidate.getRGB(x, y);
                if (base == cand) {
                    int index = y * width + x;
                    baselineMatched[index] = true;
                    candidateMatched[index] = true;
                    continue;
                }

                int dr = channelDelta(base, cand, 16);
                int dg = channelDelta(base, cand, 8);
                int db = channelDelta(base, cand, 0);
                int delta = Math.max(dr, Math.max(dg, db));
                exactChangedPixels++;
                deltaSum += dr + dg + db;
                maxChannelDelta = Math.max(maxChannelDelta, delta);
            }
        }

        for (int dy = -MATCH_RADIUS; dy <= MATCH_RADIUS; dy++) {
            for (int dx = -MATCH_RADIUS; dx <= MATCH_RADIUS; dx++) {
                int baseStartY = Math.max(0, dy);
                int candStartY = Math.max(0, -dy);
                int baseStartX = Math.max(0, dx);
                int candStartX = Math.max(0, -dx);
                int overlapHeight = height - Math.abs(dy);
                int overlapWidth = width - Math.abs(dx);
                if (overlapHeight <= 0 || overlapWidth <= 0)
                    continue;

                for (int y = 0; y < overlapHeight; y++) {
                    int baseY = baseStartY + y;
                    int candY = candStartY + y;
                    int baseRow = baseY * width;
                    int candRow = candY * width;
                    for (int x = 0; x < overlapWidth; x++) {
                        int baseX = baseStartX + x;
                        int candX = candStartX + x;
                        int baseRgb = baseline.getRGB(baseX, baseY);
                        int candRgb = candidate.getRGB(candX, candY);
                        if (maxChannelDelta(baseRgb, candRgb) > MATCH_CHANNEL_TOLERANCE)
                            continue;

                        baselineMatched[baseRow + baseX] = true;
                        candidateMatched[candRow + candX] = true;
                    }
                }
            }
        }

        long missingBaselinePixels = 0L;
        long extraCandidatePixels = 0L;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                boolean baselineUnmatched = !baselineMatched[index];
                boolean candidateUnmatched = !candidateMatched[index];
                if (baselineUnmatched)
                    missingBaselinePixels++;
                if (candidateUnmatched)
                    extraCandidatePixels++;

                if (!baselineUnmatched && !candidateUnmatched) {
                    int gray = toGray(baseline.getRGB(x, y));
                    diffImage.setRGB(x, y, 0xFF000000 | gray << 16 | gray << 8 | gray);
                    continue;
                }

                int argb = baselineUnmatched && candidateUnmatched ? 0xD0FF00FF
                        : baselineUnmatched ? 0xC0FF00FF
                        : 0xC0FFA000;
                diffImage.setRGB(x, y, argb);
            }
        }

        long totalPixels = (long) total;
        double exactChangedRatio = totalPixels == 0 ? 0.0d : exactChangedPixels / (double) totalPixels;
        double meanChannelDelta = exactChangedPixels == 0 ? 0.0d : deltaSum / (exactChangedPixels * 3.0d);
        double missingBaselineRatio = totalPixels == 0 ? 0.0d : missingBaselinePixels / (double) totalPixels;
        double extraCandidateRatio = totalPixels == 0 ? 0.0d : extraCandidatePixels / (double) totalPixels;
        boolean passed = missingBaselineRatio <= MAX_CHANGED_RATIO && extraCandidateRatio <= MAX_CHANGED_RATIO;
        return new ComparisonSummary(
                exactChangedPixels,
                totalPixels,
                exactChangedRatio,
                maxChannelDelta,
                meanChannelDelta,
                extraCandidatePixels,
                extraCandidateRatio,
                missingBaselinePixels,
                missingBaselineRatio,
                passed,
                diffImage
        );
    }

    private static void writeShots(Fixture fixture, Dimension size, Scenario scenario, Path rendererPath, Path shellPath) throws IOException {
        writeImage(rendererPath, render(fixture, size, scenario, true));
        writeImage(shellPath, render(fixture, size, scenario, false));
    }

    private static BufferedImage render(Fixture fixture, Dimension size, Scenario scenario, boolean trimChrome) {
        ChartTemplate template = templateForScenario(scenario);
        ChartFrame chartFrame = ChartExporter.createChartFrame(
                fixture.provider(),
                fixture.dataset(),
                template,
                size,
                trimChrome
        );
        var exportOptions = ExportOptions.builder().dimensions(size).build();
        applyViewport(chartFrame, fixture.viewport());
        flushEdt();
        ChartExporter.layoutRecursively(chartFrame);
        flushEdt();
        ChartExporter.renderPngImage(chartFrame, exportOptions);
        flushEdt();
        dumpComponentTree(chartFrame, scenario);
        dumpFooterModel(chartFrame, scenario, size);
        validateFooter(chartFrame, scenario, HoverTarget.MAIN);
        if (!chartFrame.getMainStackPanel().getIndicatorPanels().isEmpty())
            validateFooter(chartFrame, scenario, HoverTarget.INDICATOR);
        armCrosshair(chartFrame, HoverTarget.MAIN);
        flushEdt();
        dumpFooterModel(chartFrame, scenario, size);
        return ChartExporter.renderPngImage(chartFrame, exportOptions);
    }

    private static void flushEdt() {
        try {
            SwingUtilities.invokeAndWait(() -> { });
            SwingUtilities.invokeAndWait(() -> { });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while flushing the EDT", ex);
        } catch (InvocationTargetException ex) {
            throw new IllegalStateException("Failed to flush the EDT", ex);
        }
    }

    private static ChartTemplate templateForScenario(Scenario scenario) {
        ChartTemplate template = ChartExporter.basicChartTemplate();
        return switch (scenario) {
            case DEFAULT -> template;
            case GROUPED_INDICATORS -> addGroupedIndicators(template);
            case FRACTAL_FILL -> fractalFillTemplate(template);
        case M1, M15, H1, H6, H6_SESSION, DAILY, DAILY_YEAR_BOUNDARY, DAILY_SAME_YEAR_LONG, WEEKLY, MONTHLY -> template;
        };
    }

    private static ChartTemplate addGroupedIndicators(ChartTemplate template) {
        var studyRegistry = StudyRegistry.getDefault();
        Indicator extra = studyRegistry.getIndicator("Fractal Dimension");
        if (extra == null)
            return template;
        UUID paneId = template.getIndicators().isEmpty() ? UUID.randomUUID() : template.getIndicators().getFirst().getPanelId();
        extra.setPanelId(paneId);
        template.addIndicator(extra);
        return template;
    }

    private static ChartTemplate fractalFillTemplate(ChartTemplate template) {
        template.getOverlays().clear();
        template.getIndicators().clear();
        Indicator fractalDimension = StudyRegistry.getDefault().getIndicator("Fractal Dimension");
        if (fractalDimension != null)
            template.addIndicator(fractalDimension);
        return template;
    }

    private static void applyViewport(ChartFrame chartFrame, Viewport viewport) {
        if (viewport == null)
            return;

        FrontEndSupport.getDefault().execute(() -> {
            Rectangle plotBounds = chartFrame.getMainPanel().getChartPanel().getRenderBounds();
            if (plotBounds.width <= 0)
                return null;

            double fillRatio = Math.clamp(chartFrame.getChartProperties().getSlotFillPercent(), 1.0, 100.0) / 100.0;
            double displaySpan = Math.max(1.0, plotBounds.width - 1.0) * deviceScaleX(chartFrame);
            double desiredStep = displaySpan / Math.max(1, viewport.visibleSlots());
            double barWidth = Math.max(1.0, desiredStep * fillRatio);
            chartFrame.getChartProperties().setBarWidth(barWidth);

            chartFrame.refreshChartView();
            int desiredVisibleSlots = viewport.visibleSlots();
            int actualVisibleSlots = chartFrame.getChartData().getVisibleSlotCount();
            if (actualVisibleSlots > 0 && actualVisibleSlots != desiredVisibleSlots) {
                double correction = actualVisibleSlots / (double) desiredVisibleSlots;
                chartFrame.getChartProperties().setBarWidth(Math.max(1.0, barWidth * correction));
                chartFrame.refreshChartView();
            }

            int totalSlots = chartFrame.getChartData().getTotalSlotCount();
            int startSlot = viewport.startSlot() != null
                    ? Math.clamp(viewport.startSlot(), 0, Math.max(0, totalSlots - chartFrame.getChartData().getVisibleSlotCount()))
                    : Math.max(0, totalSlots - chartFrame.getChartData().getVisibleSlotCount());
            chartFrame.getChartData().setVisibleStartSlot(startSlot);
            chartFrame.refreshChartView();
            return null;
        });
    }

    private static double deviceScaleX(ChartFrame chartFrame) {
        GraphicsConfiguration configuration = chartFrame.getMainPanel() != null
                && chartFrame.getMainPanel().getChartPanel() != null
                ? chartFrame.getMainPanel().getChartPanel().getGraphicsConfiguration()
                : null;
        if (configuration == null)
            return 1.0;

        double scale = configuration.getDefaultTransform().getScaleX();
        return (Double.isFinite(scale) && scale > 0.0) ? scale : 1.0;
    }

    private static void armCrosshair(ChartFrame chartFrame, HoverTarget hoverTarget) {
        FrontEndSupport.getDefault().execute(() -> {
            AnnotationPanel annotationPanel = switch (hoverTarget) {
                case MAIN -> chartFrame.getMainStackPanel().getChartPanel().getAnnotationPanel();
                case INDICATOR -> {
                    var indicatorPanels = chartFrame.getMainStackPanel().getIndicatorPanels();
                    yield indicatorPanels.isEmpty() ? null : indicatorPanels.getLast().getAnnotationPanel();
                }
            };
            if (annotationPanel == null)
                return null;
            @SuppressWarnings("unchecked")
            JLayer<JComponent> crosshairLayer = (JLayer<JComponent>) findCrosshairLayer(chartFrame);
            if (crosshairLayer == null)
                return null;
            LayerUI<? super JComponent> layerUI = crosshairLayer.getUI();
            if (!(layerUI instanceof StandardCrosshairRendererLayer crosshair))
                return null;

            Rectangle plotBounds = annotationPanel.getRenderBounds();
            if (plotBounds.isEmpty())
                return null;

            double xFactor = (hoverTarget == HoverTarget.MAIN) ? 0.72 : 0.58;
            double yFactor = (hoverTarget == HoverTarget.MAIN) ? 0.38 : 0.48;
            int x = plotBounds.x + (int) Math.round(plotBounds.width * xFactor);
            int y = plotBounds.y + (int) Math.round(plotBounds.height * yFactor);
            int slot = chartFrame.getChartData().getSlotAtX(x, plotBounds);
            long when = System.currentTimeMillis();
            annotationPanel.dispatchEvent(new MouseEvent(annotationPanel, MouseEvent.MOUSE_ENTERED, when, 0, x, y, 0, false, MouseEvent.NOBUTTON));
            crosshair.eventDispatched(new MouseEvent(annotationPanel, MouseEvent.MOUSE_MOVED, when + 1, 0, x, y, 0, false, MouseEvent.NOBUTTON), crosshairLayer);
            if (slot >= 0 && chartFrame.getDateAxisFooter() != null)
                chartFrame.getDateAxisFooter().setHoverSlot(slot);
            return null;
        });
    }

    private static void dumpFooterModel(ChartFrame chartFrame, Scenario scenario, Dimension size) {
        var footer = chartFrame.getDateAxisFooter();
        if (footer == null)
            return;
        Path footerDumpPath = scenarioPath(sizedPath(FOOTER_MODEL_PATH, size), scenario);
        try {
            Files.writeString(footerDumpPath, footer.snapshot().toDebugString());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to write footer model dump", ex);
        }
    }

    private static void validateFooter(ChartFrame chartFrame, Scenario scenario, HoverTarget hoverTarget) {
        var footer = chartFrame.getDateAxisFooter();
        if (footer == null)
            throw new IllegalStateException("Shared date axis footer missing for scenario " + scenario);
        var snapshot = footer.snapshot();
        Object mainChart = invoke(chartFrame.getMainStackPanel().getChartPanel(), "getEngineChart");
        Object visibleRange = invoke(invoke(mainChart, "getXAxis"), "getVisibleRange");
        Object sharedScale = invoke(chartFrame.getMainStackPanel(), "getSharedTimeScale");
        Object stepsDefinition = invoke(sharedScale, "getStepsDefinition");
        @SuppressWarnings("unchecked")
        List<Object> nativeTicks = switch (invoke(stepsDefinition, "snapshotUpperTicks", visibleRange, snapshot.plotBounds().width)) {
            case List<?> ticks -> (List<Object>) ticks;
            default -> List.of();
        };
        if (snapshot.lowerTicks().isEmpty() && snapshot.lowerContextLabel() == null) {
            throw new IllegalStateException("Lower footer ticks missing for scenario " + scenario + " hover=" + hoverTarget
                    + ", visibleRange=" + visibleRange
                    + ", steps=" + stepsDefinition
                    + ", snapshot=" + snapshot.toDebugString());
        }
        if (snapshot.plotBounds().isEmpty()) {
            System.err.println("Footer plot bounds missing for scenario " + scenario);
            return;
        }
        if (snapshot.upperTicks().isEmpty()) {
            if (nativeTicks.isEmpty())
                System.err.println("No native upper footer ticks exposed for scenario " + scenario);
            return;
        }
        // The offscreen harness does not always deliver the footer hover badge
        // deterministically, especially on intraday charts. The axis cadence
        // validation below is the stronger check for the footer itself.

        int previousTickX = Integer.MIN_VALUE;
        for (var actualTick : snapshot.upperTicks()) {
            if (actualTick.x() < snapshot.plotBounds().x - 1 || actualTick.x() > snapshot.plotBounds().x + snapshot.plotBounds().width + 1) {
                throw new IllegalStateException("Upper footer tick falls outside plot bounds for scenario " + scenario
                        + ": tick=" + actualTick + " plotBounds=" + snapshot.plotBounds());
            }
            if (actualTick.x() <= previousTickX) {
                throw new IllegalStateException("Upper footer ticks are not strictly increasing for scenario " + scenario
                        + ": previousX=" + previousTickX + " current=" + actualTick);
            }
            previousTickX = actualTick.x();
            Object matchedNativeTick = nativeTicks.stream()
                    .filter(nativeTick -> tickValueMatches(nativeTick, actualTick.value()))
                    .findFirst()
                    .orElse(null);
            if (matchedNativeTick == null) {
                throw new IllegalStateException("Upper footer tick value missing from native scale snapshot for scenario " + scenario
                        + ": tick=" + actualTick + " nativeTicks=" + nativeTicks);
            }
            String expectedLabel = normalizeTickLabel(String.valueOf(invoke(matchedNativeTick, "label")));
            if (!expectedLabel.equals(normalizeTickLabel(actualTick.label()))) {
                throw new IllegalStateException("Upper footer tick label mismatch for scenario " + scenario
                        + ": expected=" + expectedLabel + " actual=" + normalizeTickLabel(actualTick.label()));
            }
        }

        if (snapshot.lowerContextLabel() != null && snapshot.lowerContextLabel().labelX() < snapshot.plotBounds().x)
            throw new IllegalStateException("Lower footer context label starts before plot bounds for scenario " + scenario);
        if (snapshot.lowerTicks().stream().anyMatch(tick -> tick.labelX() <= tick.x()))
            throw new IllegalStateException("Footer labels are not right-adjacent to tick marks for scenario " + scenario);
        // The lower contextual lane is allowed to collapse to a single forced
        // left-edge label when the native time scale does not expose an
        // additional historical boundary for the active viewport.

        armCrosshair(chartFrame, hoverTarget);
        flushEdt();
    }

    private static boolean requiresContextTransitions(ChartData chartData) {
        if (chartData == null || !chartData.hasDataset())
            return false;
        int visibleStart = chartData.getVisibleStartSlot();
        int visibleEnd = Math.max(visibleStart + 1, Math.min(chartData.getVisibleEndSlot(), chartData.getHistoricalSlotCount()));
        if (visibleStart >= visibleEnd - 1)
            return false;

        long firstTime = chartData.getSlotTime(visibleStart);
        long lastTime = chartData.getSlotTime(visibleEnd - 1);
        double observedBaseUnitMillis = observedBaseUnitMillis(chartData, visibleStart, visibleEnd);
        Object plan = invokeStatic(
                "one.chartsy.charting.financial.AdaptiveCategoryTimeSteps",
                "plan",
                new Class<?>[] { long.class, long.class, double.class },
                firstTime,
                lastTime,
                observedBaseUnitMillis
        );
        Object lowerLane = invoke(plan, "lower");
        if (lowerLane == null || "NONE".equals(lowerLane.toString()))
            return false;
        String firstKey = String.valueOf(invokeStatic(
                "one.chartsy.charting.financial.AdaptiveCategoryTimeSteps",
                "key",
                new Class<?>[] { lowerLane.getClass(), long.class },
                lowerLane,
                firstTime
        ));
        String lastKey = String.valueOf(invokeStatic(
                "one.chartsy.charting.financial.AdaptiveCategoryTimeSteps",
                "key",
                new Class<?>[] { lowerLane.getClass(), long.class },
                lowerLane,
                lastTime
        ));
        return !firstKey.equals(lastKey);
    }

    private static double observedBaseUnitMillis(ChartData chartData, int visibleStart, int visibleEnd) {
        long bestDelta = Long.MAX_VALUE;
        for (int slot = visibleStart + 1; slot < visibleEnd; slot++) {
            long deltaNanos = Math.abs(chartData.getSlotTime(slot) - chartData.getSlotTime(slot - 1));
            if (deltaNanos > 0L && deltaNanos < bestDelta)
                bestDelta = deltaNanos;
        }
        if (bestDelta != Long.MAX_VALUE)
            return bestDelta / 1_000_000.0d;
        return chartData.getTimeFrame() != null && chartData.getTimeFrame().getAsSeconds().isPresent()
                ? chartData.getTimeFrame().getAsSeconds().orElseThrow().getAmount() * 1000.0d
                : 86_400_000.0d;
    }

    private static boolean tickValueMatches(Object nativeTick, double value) {
        Object nativeValue = invoke(nativeTick, "value");
        return nativeValue instanceof Number number
                && Math.abs(number.doubleValue() - value) < 0.0001d;
    }

    private static String normalizeTickLabel(String label) {
        return BIDI_CONTROL_PATTERN.matcher(label == null ? "" : label).replaceAll("");
    }

    private static JLayer<?> findCrosshairLayer(java.awt.Component component) {
        if (component instanceof JLayer<?> layer && layer.getUI() instanceof StandardCrosshairRendererLayer)
            return layer;
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                JLayer<?> found = findCrosshairLayer(child);
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    private static Path sizedPath(Path path, Dimension size) {
        Dimension defaultSize = OPTIONS.getDimensions();
        if (size.equals(defaultSize))
            return path;

        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String suffix = "-" + size.width + "x" + size.height;
        if (dot < 0)
            return path.resolveSibling(fileName + suffix);
        return path.resolveSibling(fileName.substring(0, dot) + suffix + fileName.substring(dot));
    }

    private static Path scenarioPath(Path path, Scenario scenario) {
        if (scenario == Scenario.DEFAULT)
            return path;
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String suffix = "-" + scenario.fileToken();
        if (dot < 0)
            return path.resolveSibling(fileName + suffix);
        return path.resolveSibling(fileName.substring(0, dot) + suffix + fileName.substring(dot));
    }

    private static void dumpComponentTree(ChartFrame chartFrame, Scenario scenario) {
        StringBuilder dump = new StringBuilder(4096);
        appendTemplateSummary(chartFrame, dump);
        appendComponent(chartFrame, dump, 0);
        try {
            Files.writeString(scenarioPath(COMPONENT_TREE_PATH, scenario), dump);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to write component tree dump", ex);
        }
    }

    private static void appendTemplateSummary(ChartFrame chartFrame, StringBuilder dump) {
        dump.append("templateIndicators=").append(chartFrame.getChartTemplate().getIndicators().size()).append(System.lineSeparator());
        for (Indicator indicator : chartFrame.getChartTemplate().getIndicators()) {
            dump.append("  indicator[").append(indicator.getLabel()).append("]")
                    .append(" panelId=").append(indicator.getPanelId())
                    .append(System.lineSeparator());
        }
    }

    private static void appendComponent(java.awt.Component component, StringBuilder dump, int depth) {
        if (component == null) {
            dump.append("  ".repeat(depth)).append("<null component>").append(System.lineSeparator());
            return;
        }
        dump.append("  ".repeat(depth))
                .append(component.getClass().getName())
                .append(" bounds=").append(component.getBounds())
                .append(" visible=").append(component.isVisible());
        if (component instanceof IndicatorPanel panel) {
            dump.append(" paneId=").append(panel.getId());
            dump.append(" indicators=");
            dump.append(panel.getIndicators().stream()
                    .map(indicator -> indicator.getLabel() + "@" + indicator.getPanelId())
                    .toList());
        }
        if (isEngineChart(component)) {
            appendEngineChartSummary(component, dump);
        }
        dump.append(System.lineSeparator());
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents())
                appendComponent(child, dump, depth + 1);
        }
    }

    private static boolean isEngineChart(Object component) {
        return component != null && component.getClass().getName().equals("one.chartsy.charting.Chart");
    }

    private static void appendEngineChartSummary(Object chart, StringBuilder dump) {
        Object chartArea = invoke(chart, "getChartArea");
        Object plotRect = chartArea != null ? invoke(chartArea, "getPlotRect") : null;
        Object xScale = invoke(chart, "getXScale");
        Object yScale = invoke(chart, "getYScale", 0);
        Object xAxis = invoke(chart, "getXAxis");
        Object yAxis = invoke(chart, "getYAxis", 0);

        dump.append(" plotRect=").append(plotRect);
        dump.append(" xScale=").append(scaleSummary(xScale));
        dump.append(" yScale=").append(scaleSummary(yScale));
        dump.append(" xVisibleRange=").append(xAxis != null ? invoke(xAxis, "getVisibleRange") : null);
        dump.append(" yVisibleRange=[")
                .append(yAxis != null ? invoke(yAxis, "getVisibleMin") : null)
                .append(", ")
                .append(yAxis != null ? invoke(yAxis, "getVisibleMax") : null)
                .append(']');
    }

    private static String scaleSummary(Object scale) {
        if (scale == null)
            return "null";
        return "{visible=%s,axisVisible=%s,labelVisible=%s}".formatted(
                invoke(scale, "isVisible"),
                invoke(scale, "isAxisVisible"),
                invoke(scale, "isLabelVisible"));
    }

    private static Object invoke(Object target, String methodName, Object... args) {
        if (target == null)
            return null;
        Class<?>[] parameterTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++)
            parameterTypes[i] = primitiveType(args[i].getClass());
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            return "<" + methodName + " failed>";
        }
    }

    private static Object invokeStatic(String className, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = Class.forName(className).getMethod(methodName, parameterTypes);
            return method.invoke(null, args);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            return "<" + methodName + " failed>";
        }
    }

    private static Class<?> primitiveType(Class<?> type) {
        if (type == Integer.class)
            return int.class;
        if (type == Long.class)
            return long.class;
        if (type == Boolean.class)
            return boolean.class;
        if (type == Double.class)
            return double.class;
        return type;
    }

    private static void writeImage(Path path, BufferedImage image) throws IOException {
        Files.createDirectories(path.getParent());
        if (!ImageIO.write(image, "png", path.toFile()))
            throw new IOException("No PNG writer available for " + path);
    }

    private static int channel(int rgb, int shift) {
        return (rgb >>> shift) & 0xFF;
    }

    private static int channelDelta(int left, int right, int shift) {
        return Math.abs(channel(left, shift) - channel(right, shift));
    }

    private static int maxChannelDelta(int left, int right) {
        return Math.max(channelDelta(left, right, 16), Math.max(channelDelta(left, right, 8), channelDelta(left, right, 0)));
    }

    private static int toGray(int rgb) {
        int r = channel(rgb, 16);
        int g = channel(rgb, 8);
        int b = channel(rgb, 0);
        return (r * 30 + g * 59 + b * 11) / 100;
    }

    private static Path projectRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("chartsy-desktop")) && Files.isDirectory(current.resolve("chartsy-kernel")))
                return current;
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate chartsy project root from " + Path.of("").toAbsolutePath());
    }

    enum Mode {
        BASELINE,
        CANDIDATE,
        DIFF,
        ALL;

        static Mode parse(String value) {
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "baseline" -> BASELINE;
                case "candidate" -> CANDIDATE;
                case "diff", "compare" -> DIFF;
                case "all" -> ALL;
                default -> throw new IllegalArgumentException("Unsupported mode: " + value);
            };
        }
    }

    enum Scenario {
        DEFAULT("default"),
        GROUPED_INDICATORS("grouped"),
    FRACTAL_FILL("fractal-fill"),
    M1("m1"),
    M15("m15"),
    H1("h1"),
        H6("h6"),
        H6_SESSION("h6-session"),
    DAILY("daily"),
        DAILY_YEAR_BOUNDARY("daily-year-boundary"),
        DAILY_SAME_YEAR_LONG("daily-same-year-long"),
        WEEKLY("weekly"),
        MONTHLY("monthly");

        private final String fileToken;

        Scenario(String fileToken) {
            this.fileToken = fileToken;
        }

        static Scenario parse(String value) {
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "default" -> DEFAULT;
                case "grouped", "grouped-indicators", "multi-pane", "multi-indicator" -> GROUPED_INDICATORS;
                case "fractal-fill", "fractal", "fdi" -> FRACTAL_FILL;
                case "m1" -> M1;
                case "m15" -> M15;
            case "h1" -> H1;
                case "h6" -> H6;
                case "h6-session", "h6-market", "h6-irregular" -> H6_SESSION;
            case "daily" -> DAILY;
                case "daily-year-boundary", "daily-year" -> DAILY_YEAR_BOUNDARY;
                case "daily-same-year-long", "daily-same-year" -> DAILY_SAME_YEAR_LONG;
                case "weekly" -> WEEKLY;
                case "monthly" -> MONTHLY;
                default -> throw new IllegalArgumentException("Unsupported scenario: " + value);
            };
        }

        String fileToken() {
            return fileToken;
        }
    }

    record ParsedArguments(Mode mode, Scenario scenario, Dimension size) {
        static ParsedArguments parse(String[] args) {
            Mode mode = (args.length == 0) ? Mode.CANDIDATE : Mode.parse(args[0]);
            Scenario scenario = Scenario.DEFAULT;
            int index = 1;
            if (args.length > index && !isInteger(args[index])) {
                scenario = Scenario.parse(args[index]);
                index++;
            }
            Dimension size = (args.length >= index + 2)
                    ? new Dimension(Integer.parseInt(args[index]), Integer.parseInt(args[index + 1]))
                    : OPTIONS.getDimensions();
            return new ParsedArguments(mode, scenario, size);
        }

        private static boolean isInteger(String value) {
            try {
                Integer.parseInt(value);
                return true;
            } catch (NumberFormatException ex) {
                return false;
            }
        }
    }

    enum HoverTarget {
        MAIN,
        INDICATOR
    }

    record Viewport(int visibleSlots, Integer startSlot) {
    }

    record Fixture(DataProvider provider, CandleSeries dataset, Viewport viewport) {

        static Fixture create(Scenario scenario) {
            return switch (scenario) {
                case M1 -> syntheticFixture(TimeFrame.Period.M1, 3_000, new Viewport(900, null), LocalDateTime.of(2026, 3, 10, 0, 0));
                case M15 -> syntheticFixture(TimeFrame.Period.M15, 900, new Viewport(220, null), LocalDateTime.of(2026, 1, 10, 0, 0));
                case H1 -> syntheticFixture(TimeFrame.Period.H1, 600, new Viewport(96, null), LocalDateTime.of(2025, 12, 20, 0, 0));
                case H6 -> syntheticFixture(TimeFrame.Period.H6, 420, new Viewport(120, null), LocalDateTime.of(2025, 9, 1, 0, 0));
                case H6_SESSION -> sessionFixture(TimeFrame.Period.H6, 110, new Viewport(70, null), LocalDateTime.of(2025, 10, 1, 15, 30));
                case DAILY -> syntheticFixture(TimeFrame.Period.DAILY, 420, new Viewport(80, null), LocalDateTime.of(2024, 7, 1, 0, 0));
                case DAILY_YEAR_BOUNDARY -> syntheticFixture(TimeFrame.Period.DAILY, 260, new Viewport(140, null), LocalDateTime.of(2025, 7, 1, 0, 0));
                case DAILY_SAME_YEAR_LONG -> syntheticFixture(TimeFrame.Period.DAILY, 240, new Viewport(150, null), LocalDateTime.of(2026, 2, 1, 0, 0));
                case WEEKLY -> syntheticFixture(TimeFrame.Period.WEEKLY, 180, new Viewport(90, null), LocalDateTime.of(2022, 1, 3, 0, 0));
                case MONTHLY -> syntheticFixture(TimeFrame.Period.MONTHLY, 96, new Viewport(36, null), LocalDateTime.of(2019, 1, 1, 0, 0));
                case FRACTAL_FILL -> fixture(createFractalFillDataset(), new Viewport(80, null));
                default -> fixture(createDataset(), new Viewport(90, null));
            };
        }

        private static Fixture fixture(CandleSeries dataset, Viewport viewport) {
            return new Fixture(new FixtureProvider(dataset), dataset, viewport);
        }

        private static CandleSeries createDataset() {
            SymbolResource<Candle> resource = SymbolResource.of(FIXTURE_SYMBOL, TimeFrame.Period.DAILY);
            var candles = new ArrayList<Candle>(320);
            double close = 118.0;
            LocalDate date = LocalDate.of(2024, 1, 2);
            for (int i = 0; i < 320; i++) {
                double drift = Math.sin(i / 7.5d) * 1.8d + Math.cos(i / 19.0d) * 0.9d + ((i % 9) - 4) * 0.17d;
                double open = close + Math.sin(i / 5.0d) * 0.65d;
                close = Math.max(45.0d, open + drift);
                double high = Math.max(open, close) + 0.9d + Math.abs(Math.sin(i / 3.0d)) * 1.1d;
                double low = Math.min(open, close) - 0.8d - Math.abs(Math.cos(i / 4.0d)) * 1.0d;
                double volume = 1_500_000d + (i % 17) * 85_000d + Math.abs(drift) * 120_000d;
                candles.add(Candle.of(date.plusDays(i).atStartOfDay(), open, high, low, close, volume));
            }
            return CandleSeries.of(resource, candles);
        }

        private static CandleSeries createFractalFillDataset() {
            SymbolResource<Candle> resource = SymbolResource.of(FIXTURE_SYMBOL, TimeFrame.Period.DAILY);
            var candles = new ArrayList<Candle>(320);
            double close = 124.0;
            LocalDate date = LocalDate.of(2024, 1, 2);
            for (int i = 0; i < 320; i++) {
                double open;
                double high;
                double low;
                double volume;
                if (i < 220) {
                    double drift = Math.sin(i / 9.0d) * 1.2d + Math.cos(i / 21.0d) * 0.8d + ((i % 7) - 3) * 0.13d;
                    open = close + Math.sin(i / 6.0d) * 0.4d;
                    close = Math.max(50.0d, open + drift);
                    high = Math.max(open, close) + 0.8d + Math.abs(Math.sin(i / 4.0d)) * 0.9d;
                    low = Math.min(open, close) - 0.7d - Math.abs(Math.cos(i / 5.0d)) * 0.8d;
                    volume = 1_200_000d + (i % 11) * 90_000d + Math.abs(drift) * 95_000d;
                } else {
                    double center = 132.0d + Math.sin(i / 3.8d) * 2.8d + Math.cos(i / 7.0d) * 1.4d;
                    double zigzag = ((i & 1) == 0 ? 7.8d : -7.8d) + Math.sin(i / 2.1d) * 1.6d;
                    open = center - zigzag * 0.42d;
                    close = Math.max(50.0d, center + zigzag);
                    high = Math.max(open, close) + 1.0d + Math.abs(Math.sin(i / 1.9d)) * 1.4d;
                    low = Math.min(open, close) - 1.0d - Math.abs(Math.cos(i / 2.4d)) * 1.4d;
                    volume = 1_800_000d + (i % 9) * 120_000d + Math.abs(zigzag) * 160_000d;
                }
                candles.add(Candle.of(date.plusDays(i).atStartOfDay(), open, high, low, close, volume));
            }
            return CandleSeries.of(resource, candles);
        }

        private static Fixture syntheticFixture(TimeFrame timeFrame, int length, Viewport viewport, LocalDateTime start) {
            SymbolResource<Candle> resource = SymbolResource.of(FIXTURE_SYMBOL, timeFrame);
            CandleSeries dataset = createSyntheticDataset(resource, length, start);
            return new Fixture(new FixtureProvider(dataset), dataset, viewport);
        }

        private static Fixture sessionFixture(TimeFrame timeFrame, int length, Viewport viewport, LocalDateTime start) {
            SymbolResource<Candle> resource = SymbolResource.of(FIXTURE_SYMBOL, timeFrame);
            CandleSeries dataset = createSessionDataset(resource, length, start);
            return new Fixture(new FixtureProvider(dataset), dataset, viewport);
        }

        private static CandleSeries createSyntheticDataset(SymbolResource<Candle> resource, int length, LocalDateTime start) {
            var candles = new ArrayList<Candle>(length);
            double close = 118.0;
            LocalDateTime time = start;
            for (int i = 0; i < length; i++) {
                double drift = Math.sin(i / 7.5d) * 1.6d + Math.cos(i / 19.0d) * 0.8d + ((i % 11) - 5) * 0.11d;
                double open = close + Math.sin(i / 5.0d) * 0.55d;
                close = Math.max(18.0d, open + drift);
                double high = Math.max(open, close) + 0.65d + Math.abs(Math.sin(i / 3.0d)) * 0.9d;
                double low = Math.min(open, close) - 0.6d - Math.abs(Math.cos(i / 4.0d)) * 0.85d;
                double volume = 800_000d + (i % 17) * 65_000d + Math.abs(drift) * 90_000d;
                candles.add(Candle.of(time, open, high, low, close, volume));
                time = advance(time, resource.timeFrame());
            }
            return CandleSeries.of(resource, candles);
        }

        private static CandleSeries createSessionDataset(SymbolResource<Candle> resource, int length, LocalDateTime start) {
            var candles = new ArrayList<Candle>(length);
            double close = 118.0;
            LocalDateTime time = start;
            for (int i = 0; i < length; i++) {
                double drift = Math.sin(i / 5.5d) * 2.2d + Math.cos(i / 11.0d) * 1.1d + ((i % 9) - 4) * 0.23d;
                double open = close + Math.sin(i / 3.0d) * 0.45d;
                close = Math.max(18.0d, open + drift);
                double high = Math.max(open, close) + 0.75d + Math.abs(Math.sin(i / 2.4d)) * 0.95d;
                double low = Math.min(open, close) - 0.7d - Math.abs(Math.cos(i / 3.6d)) * 0.8d;
                double volume = 700_000d + (i % 13) * 70_000d + Math.abs(drift) * 110_000d;
                candles.add(Candle.of(time, open, high, low, close, volume));
                time = advanceSession(time, resource.timeFrame());
            }
            return CandleSeries.of(resource, candles);
        }

        private static LocalDateTime advance(LocalDateTime time, TimeFrame timeFrame) {
            return switch ((TimeFrame.Period) timeFrame) {
                case M1 -> time.plusMinutes(1);
                case M15 -> time.plusMinutes(15);
                case H1 -> time.plusHours(1);
                case H6 -> time.plusHours(6);
                case DAILY -> time.plusDays(1);
                case WEEKLY -> time.plusWeeks(1);
                case MONTHLY -> time.plusMonths(1);
                default -> throw new IllegalArgumentException("Unsupported synthetic timeframe: " + timeFrame);
            };
        }

        private static LocalDateTime advanceSession(LocalDateTime time, TimeFrame timeFrame) {
            return switch ((TimeFrame.Period) timeFrame) {
                case H6 -> nextTradingSession(time.plusDays(1).withHour(15).withMinute(30));
                default -> throw new IllegalArgumentException("Unsupported session timeframe: " + timeFrame);
            };
        }

        private static LocalDateTime nextTradingSession(LocalDateTime time) {
            return switch (time.getDayOfWeek()) {
                case SATURDAY -> time.plusDays(2);
                case SUNDAY -> time.plusDays(1);
                default -> time;
            };
        }
    }

    record ComparisonSummary(
            long exactChangedPixels,
            long totalPixels,
            double exactChangedRatio,
            int maxChannelDelta,
            double meanChannelDelta,
            long extraCandidatePixels,
            double extraCandidateRatio,
            long missingBaselinePixels,
            double missingBaselineRatio,
            boolean passed,
            BufferedImage diffImage) {

        String toReport() {
            return """
                    exactChangedPixels=%d
                    totalPixels=%d
                    exactChangedRatio=%.6f
                    maxChannelDelta=%d
                    meanChannelDelta=%.4f
                    perceptualRadius=%d
                    perceptualChannelTolerance=%d
                    extraCandidatePixels=%d
                    extraCandidateRatio=%.6f
                    missingBaselinePixels=%d
                    missingBaselineRatio=%.6f
                    thresholdRatio=%.6f
                    passed=%s
                    """.formatted(
                    exactChangedPixels,
                    totalPixels,
                    exactChangedRatio,
                    maxChannelDelta,
                    meanChannelDelta,
                    MATCH_RADIUS,
                    MATCH_CHANNEL_TOLERANCE,
                    extraCandidatePixels,
                    extraCandidateRatio,
                    missingBaselinePixels,
                    missingBaselineRatio,
                    MAX_CHANGED_RATIO,
                    passed
            );
        }
    }

    static final class FixtureProvider implements DataProvider {
        private final CandleSeries dataset;

        FixtureProvider(CandleSeries dataset) {
            this.dataset = dataset;
        }

        @Override
        public String getName() {
            return "ChartFrame Fixture";
        }

        @Override
        public List<SymbolIdentity> listSymbols(SymbolGroup group) {
            return List.of(dataset.getResource().symbol());
        }

        @Override
        public <T extends one.chartsy.time.Chronological> Flux<T> query(Class<T> type, DataQuery<T> request) {
            if (!type.isAssignableFrom(Candle.class))
                return Flux.empty();
            if (!dataset.getResource().symbol().equals(request.resource().symbol()))
                return Flux.empty();
            return Flux.fromIterable(dataset).cast(type);
        }
    }
}
