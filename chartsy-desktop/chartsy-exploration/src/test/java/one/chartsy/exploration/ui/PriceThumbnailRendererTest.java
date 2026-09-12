package one.chartsy.exploration.ui;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.misc.PriceSeriesThumbnail;
import one.chartsy.misc.StyledValue;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.JTable;
import javax.swing.JComponent;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import static org.junit.jupiter.api.Assertions.*;

class PriceThumbnailRendererTest {
    private static final double[] SCALES = {1, 1.25, 1.5, 1.75, 2};
    private static final Color LINE = new Color(32, 99, 161);

    @Test
    void paintChart_fractional_scales_and_cell_offsets_preserve_endpoints_and_continuity() {
        var prices = prices(10, 15, 20);
        for (double scale : SCALES) {
            for (int offset : new int[] {0, 1, 3, 11}) {
                BufferedImage image = chart(prices, scale, offset, LINE);
                assertInkNear(image, (7 + offset) * scale, (5 + 28 + offset) * scale, scale);
                assertInkNear(image, (7 + 142 + offset) * scale, (5 + offset) * scale, scale);
                for (int step = 1; step < 20; step++) {
                    double fraction = step / 20.0;
                    assertInkNear(image, (7 + offset + 142 * fraction) * scale,
                            (5 + offset + 28 * (1 - fraction)) * scale, scale);
                }
            }
        }
    }

    @Test
    void paintChart_flat_and_single_price_remain_visible_at_all_scales() {
        for (double scale : SCALES) {
            BufferedImage flat = chart(prices(42, 42, 42), scale, 3, LINE);
            for (int x = 20; x < 140; x += 20)
                assertInkNear(flat, (x + 3) * scale, 22 * scale, scale);
            BufferedImage single = chart(prices(42), scale, 3, LINE);
            assertInkNear(single, 81 * scale, 22 * scale, scale);
        }
    }

    @Test
    void paintChart_flat_line_has_an_opaque_device_pixel_core_at_fractional_offsets() {
        for (double scale : SCALES) {
            for (int offset : new int[] {0, 1, 3, 11}) {
                BufferedImage image = chart(prices(42, 42, 42), scale, offset, LINE);
                int x = (int) ((70 + offset) * scale);
                int opaqueLinePixels = 0;
                for (int y = 0; y < image.getHeight(); y++)
                    if (image.getRGB(x, y) == LINE.getRGB())
                        opaqueLinePixels++;
                assertEquals((int) (1.5 * Math.max(1, Math.round(.8 * scale))), opaqueLinePixels,
                        "Flat prices should have a crisp full-color stroke, rather than blur across half-covered rows; scale="
                                + scale + ", logical offset=" + offset);
            }
        }
    }

    @Test
    void paintChart_reference_line_remains_one_device_pixel_at_all_scales() {
        for (double scale : SCALES) {
            for (int offset : new int[] {0, 1, 3, 11}) {
                var image = chart(prices(10, 20, 20), scale, offset, LINE);
                int x = (int) ((100 + offset) * scale);
                int baseline = (int) Math.floor((33 + offset) * scale);
                int paintedRows = 0;
                for (int y = baseline - 2; y <= baseline + 2; y++)
                    if (image.getRGB(x, y) != Color.WHITE.getRGB())
                        paintedRows++;
                assertEquals(1, paintedRows, "Only the price path gets thicker; scale=" + scale);
                var actual = new Color(image.getRGB(x, baseline));
                int expectedRed = Math.round((LINE.getRed() * 55 + 255 * 200) / 255f);
                assertEquals(expectedRed, actual.getRed(), 1, "Reference opacity remains unchanged");
            }
        }
    }

    @Test
    void paintChart_small_price_variations_use_a_minimum_five_percent_span() {
        for (double scale : SCALES) {
            var image = chart(prices(100, 100.5, 101), scale, 0, LINE);
            // A 1% move must remain a small excursion, rather than fill the whole chart height.
            var bounds = strongInkBounds(image, new Rectangle((int) (15 * scale), 0,
                    (int) (126 * scale), image.getHeight()));
            assertNotNull(bounds);
            assertTrue(bounds.height <= Math.ceil(9 * scale),
                    "A 1% move must occupy less than a third of the 28px chart height at scale " + scale);
            assertTrue(bounds.height >= Math.floor(4 * scale), "Small changes should still be visible");
            assertTrue(bounds.y > 10 * scale && bounds.y + bounds.height < 28 * scale,
                    "A small move stays near the middle of the plot");
        }
    }

    @Test
    void paintChart_nonfinite_closes_leave_a_gap_instead_of_fabricating_a_segment() {
        for (double scale : SCALES) {
            BufferedImage image = chart(prices(10, 15, Double.NaN, 20, 25), scale, 0, LINE);
            assertInkNear(image, (7 + 142 / 8.0) * scale, (5 + 28 * 5 / 6.0) * scale, scale);
            assertInkNear(image, 149 * scale, 5 * scale, scale);
            assertEquals(0, strongInk(image, new Rectangle(
                    (int) (70 * scale), (int) (10 * scale), (int) (16 * scale), (int) (16 * scale))),
                    "The middle missing-price interval must not be bridged at scale " + scale);
        }
        assertEquals(0, strongInk(chart(prices(Double.NaN, Double.POSITIVE_INFINITY), 1.25, 0, LINE),
                new Rectangle(0, 0, 210, 85)));
    }

    @Test
    void paintChart_preserves_the_callers_clip_and_transform() {
        var image = new BufferedImage(240, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.scale(1.25, 1.25);
            g.translate(3, 7);
            g.clipRect(45, 0, 50, 60);
            var transform = g.getTransform();
            var clip = g.getClip().getBounds();
            PriceThumbnailRenderer.paintChart(g, prices(10, 20), 7, 5, 142, 28, LINE);
            assertEquals(transform, g.getTransform());
            assertEquals(clip, g.getClip().getBounds());
        } finally {
            g.dispose();
        }
        assertTrue(nontransparentPixels(image, new Rectangle(60, 8, 63, 76)) > 20);
        assertEquals(0, nontransparentPixels(image, new Rectangle(0, 0, 59, 100)));
        assertEquals(0, nontransparentPixels(image, new Rectangle(124, 0, 116, 100)));
    }

    @Test
    void renderer_selected_and_dark_cells_keep_contrast_and_accessible_price_description() throws Exception {
        onEdt(() -> {
            var table = new JTable();
            var renderer = new PriceThumbnailRenderer();
            for (var sample : trendSamples().entrySet()) {
                for (int state = 0; state < 3; state++) {
                    configurePalette(table, state);
                    boolean selected = state == 2;
                    renderer.getTableCellRendererComponent(table, StyledValue.of(sample.getValue()), selected, false, 0, 0);
                    assertEquals(selected ? table.getSelectionBackground() : table.getBackground(), renderer.getBackground());
                    assertEquals(selected ? table.getSelectionForeground() : table.getForeground(), renderer.getForeground());
                    assertEquals(new Dimension(156, 30), renderer.getPreferredSize());
                    assertEquals("", renderer.getText(), "Compact cells reserve their space for the line chart");
                    String description = renderer.getAccessibleContext().getAccessibleName();
                    assertTrue(description.contains(sample.getKey().label));
                    assertTrue(description.contains(sample.getValue().endDate().toString()));
                    assertTrue(description.contains("Space to preview"));
                    var ink = sample.getKey().colorOn(renderer.getBackground());
                    assertTrue(PriceThumbnailTrendTest.contrastRatio(ink, renderer.getBackground()) >= 3);
                    var image = cell(renderer, 1.25);
                    int visible = pixelsNearColor(image, ink, renderer.getBackground());
                    assertTrue(visible > 60, "Visible trend line: " + sample.getKey() + ", palette=" + state);
                    for (int y = 36; y < image.getHeight(); y++)
                        for (int x = 0; x < image.getWidth(); x++)
                            assertEquals(renderer.getBackground().getRGB(), image.getRGB(x, y),
                                    "No percentage label should occupy the bottom of a compact chart cell");
                }
            }
            renderer.getTableCellRendererComponent(table, StyledValue.of("text"), false, false, 0, 0);
            assertEquals("No price history", renderer.getAccessibleContext().getAccessibleName());
            return null;
        });
    }

    @Test
    void renderer_optional_visual_artifacts_show_real_device_scale_without_image_resizing() throws Exception {
        String directory = System.getProperty("chartsy.thumbnail.artifacts");
        if (directory == null || directory.isBlank())
            return;
        onEdt(() -> {
            Path output = Path.of(directory);
            Files.createDirectories(output);
            var montage = new BufferedImage(1040, 700, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = montage.createGraphics();
            try {
                g.setColor(new Color(225, 229, 233));
                g.fillRect(0, 0, montage.getWidth(), montage.getHeight());
                var table = new JTable();
                var renderer = new PriceThumbnailRenderer();
                var prices = trendSamples().get(PriceThumbnailTrend.BULLISH);
                for (int row = 0; row < SCALES.length; row++) {
                    double scale = SCALES[row];
                    g.setColor(Color.DARK_GRAY);
                    g.drawString(Math.round(scale * 100) + "% device scale; 30 logical px row", 12, 25 + row * 125);
                    for (int state = 0; state < 3; state++) {
                        configurePalette(table, state);
                        renderer.getTableCellRendererComponent(table, StyledValue.of(prices), state == 2, false, 0, 0);
                        BufferedImage rendered = cell(renderer, scale);
                        ImageIO.write(rendered, "png", output.resolve("thumbnail-" + Math.round(scale * 100) + "-" + state + ".png").toFile());
                        g.drawImage(rendered, 12 + state * 342, 35 + row * 125, null);
                    }
                }
                g.setColor(Color.DARK_GRAY);
                g.drawString("Light / dark / selected; original device pixels; no scaled raster thumbnails", 12, 670);
            } finally {
                g.dispose();
            }
            ImageIO.write(montage, "png", output.resolve("thumbnail-scale-montage.png").toFile());
            writeTrendArtifacts(output);
            writeCompactTableArtifact(output);
            return null;
        });
    }

    private static void writeTrendArtifacts(Path output) throws Exception {
        var montage = new BufferedImage(860, 365, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = montage.createGraphics();
        try {
            g.setColor(new Color(225, 229, 233));
            g.fillRect(0, 0, montage.getWidth(), montage.getHeight());
            g.setColor(Color.DARK_GRAY);
            g.drawString("125% device scale · compact line-only cells · light / dark / selected", 12, 22);
            var table = new JTable();
            var renderer = new PriceThumbnailRenderer();
            int row = 0;
            for (var sample : trendSamples().entrySet()) {
                assertEquals(sample.getKey(), PriceThumbnailTrend.of(sample.getValue()));
                g.setColor(Color.DARK_GRAY);
                g.drawString(sample.getKey().label, 12, 65 + row * 75);
                for (int state = 0; state < 3; state++) {
                    configurePalette(table, state);
                    renderer.getTableCellRendererComponent(table, StyledValue.of(sample.getValue()), state == 2, false, 0, 0);
                    var image = cell(renderer, 1.25);
                    ImageIO.write(image, "png", output.resolve("trend-" + sample.getKey().name().toLowerCase()
                            + "-125-" + state + ".png").toFile());
                    g.drawImage(image, 185 + state * 220, 45 + row * 75, null);
                }
                var preview = PriceThumbnailRenderer.preview(sample.getValue(), sample.getKey().name() + " SAMPLE");
                ImageIO.write(componentImage(preview, 1.25), "png", output.resolve("preview-"
                        + sample.getKey().name().toLowerCase() + "-125.png").toFile());
                if (row == 0)
                    ImageIO.write(componentImage(preview, 1.25), "png", output.resolve("thumbnail-preview-125.png").toFile());
                row++;
            }
        } finally {
            g.dispose();
        }
        ImageIO.write(montage, "png", output.resolve("thumbnail-four-trends-125.png").toFile());
    }

    private static void writeCompactTableArtifact(Path output) throws Exception {
        var samples = new ArrayList<>(trendSamples().entrySet());
        var rows = new Object[16][4];
        for (int i = 0; i < rows.length; i++) {
            var sample = samples.get(i % samples.size());
            rows[i][0] = "WSE " + (i + 1);
            rows[i][1] = StyledValue.of(sample.getValue());
            rows[i][2] = String.format(Locale.ROOT, "%.2f", sample.getValue().closeAt(sample.getValue().size() - 1));
            rows[i][3] = String.format(Locale.ROOT, "%.2f", 2.0 + i * .21);
        }
        var table = new JTable(rows, new Object[] {"Symbol", "10M price", "Last", "Inv-Z"});
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(30);
        table.setGridColor(new Color(230, 230, 230));
        table.setShowGrid(true);
        configurePalette(table, 0);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSelectionInterval(5, 5);
        table.getColumnModel().getColumn(1).setCellRenderer(new PriceThumbnailRenderer());
        var numbers = new DefaultTableCellRenderer();
        numbers.setHorizontalAlignment(DefaultTableCellRenderer.RIGHT);
        for (int i : new int[] {2, 3}) table.getColumnModel().getColumn(i).setCellRenderer(numbers);
        int[] widths = {90, 156, 85, 85};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            table.getColumnModel().getColumn(i).setWidth(widths[i]);
        }
        int width = Arrays.stream(widths).sum();
        table.setSize(width, table.getRowHeight() * table.getRowCount());
        var header = table.getTableHeader();
        header.setSize(width, 24);
        var image = new BufferedImage((int) Math.ceil(width * 1.25),
                (int) Math.ceil((table.getHeight() + header.getHeight()) * 1.25), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.scale(1.25, 1.25);
            header.paint(g);
            g.translate(0, header.getHeight());
            table.paint(g);
        } finally {
            g.dispose();
        }
        ImageIO.write(image, "png", output.resolve("thumbnail-compact-rows-125.png").toFile());
    }

    private static Map<PriceThumbnailTrend, PriceSeriesThumbnail> trendSamples() {
        var samples = new LinkedHashMap<PriceThumbnailTrend, PriceSeriesThumbnail>();
        for (var trend : List.of(PriceThumbnailTrend.BULLISH, PriceThumbnailTrend.BEARISH,
                PriceThumbnailTrend.FLAT, PriceThumbnailTrend.MIXED)) {
            double[] closes = new double[150];
            for (int i = 0; i < closes.length; i++) {
                closes[i] = switch (trend) {
                    case BULLISH -> 100 + i * .25 + 2 * Math.sin(i * .19);
                    case BEARISH -> 130 - i * .24 + 2 * Math.sin(i * .19);
                    case FLAT -> 100 + .6 * Math.sin(i * .13) + .25 * Math.sin(i * .8);
                    case MIXED -> 100 + 12 * Math.sin(i * 2 * Math.PI / 149) + 3 * Math.sin(i * .6);
                    default -> throw new AssertionError(trend);
                };
            }
            samples.put(trend, prices(closes));
        }
        return samples;
    }

    private static void configurePalette(JTable table, int state) {
        table.setBackground(state == 1 ? new Color(30, 34, 39) : Color.WHITE);
        table.setForeground(state == 1 ? new Color(235, 239, 243) : Color.DARK_GRAY);
        table.setSelectionBackground(new Color(38, 82, 130));
        table.setSelectionForeground(Color.WHITE);
    }

    private static BufferedImage componentImage(JComponent component, double scale) {
        component.setSize(component.getPreferredSize());
        var image = new BufferedImage((int) Math.ceil(component.getWidth() * scale),
                (int) Math.ceil(component.getHeight() * scale), BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try {
            g.scale(scale, scale);
            component.paint(g);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static int pixelsNearColor(BufferedImage image, Color ink, Color background) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++) {
                var color = new Color(image.getRGB(x, y));
                int distanceToInk = Math.abs(color.getRed() - ink.getRed()) + Math.abs(color.getGreen() - ink.getGreen())
                        + Math.abs(color.getBlue() - ink.getBlue());
                int distanceToBackground = Math.abs(color.getRed() - background.getRed())
                        + Math.abs(color.getGreen() - background.getGreen()) + Math.abs(color.getBlue() - background.getBlue());
                if (distanceToInk < distanceToBackground && distanceToBackground > 50) count++;
            }
        return count;
    }

    private static Rectangle strongInkBounds(BufferedImage image, Rectangle area) {
        Rectangle bounds = null;
        var clipped = area.intersection(new Rectangle(0, 0, image.getWidth(), image.getHeight()));
        for (int y = clipped.y; y < clipped.y + clipped.height; y++)
            for (int x = clipped.x; x < clipped.x + clipped.width; x++) {
                var color = new Color(image.getRGB(x, y));
                if (color.getRed() < 150 && color.getBlue() - color.getRed() > 40) {
                    var pixel = new Rectangle(x, y, 1, 1);
                    if (bounds == null) bounds = pixel;
                    else bounds.add(pixel);
                }
            }
        return bounds;
    }

    private static BufferedImage chart(PriceSeriesThumbnail prices, double scale, int offset, Color color) {
        var image = new BufferedImage((int) Math.ceil(180 * scale), (int) Math.ceil(80 * scale), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.scale(scale, scale);
            g.translate(offset, offset);
            PriceThumbnailRenderer.paintChart(g, prices, 7, 5, 142, 28, color);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static BufferedImage cell(PriceThumbnailRenderer renderer, double scale) {
        renderer.setSize(renderer.getPreferredSize());
        var image = new BufferedImage((int) Math.ceil(renderer.getWidth() * scale),
                (int) Math.ceil(renderer.getHeight() * scale), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(renderer.getBackground());
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.scale(scale, scale);
            renderer.paint(g);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void assertInkNear(BufferedImage image, double x, double y, double scale) {
        int radius = (int) Math.ceil(2 * scale);
        assertTrue(strongInk(image, new Rectangle((int) x - radius, (int) y - radius, 2 * radius + 1, 2 * radius + 1)) > 0,
                "Missing price line near device coordinate " + x + ", " + y + " at scale " + scale);
    }

    private static int strongInk(BufferedImage image, Rectangle area) {
        int count = 0;
        Rectangle clipped = area.intersection(new Rectangle(0, 0, image.getWidth(), image.getHeight()));
        for (int y = clipped.y; y < clipped.y + clipped.height; y++)
            for (int x = clipped.x; x < clipped.x + clipped.width; x++) {
                var color = new Color(image.getRGB(x, y));
                if (color.getRed() < 150 && color.getBlue() - color.getRed() > 40)
                    count++;
            }
        return count;
    }

    private static int nontransparentPixels(BufferedImage image, Rectangle area) {
        int count = 0;
        Rectangle clipped = area.intersection(new Rectangle(0, 0, image.getWidth(), image.getHeight()));
        for (int y = clipped.y; y < clipped.y + clipped.height; y++)
            for (int x = clipped.x; x < clipped.x + clipped.width; x++)
                if ((image.getRGB(x, y) >>> 24) != 0)
                    count++;
        return count;
    }

    static PriceSeriesThumbnail prices(double... closes) {
        var candles = new ArrayList<Candle>();
        LocalDate end = LocalDate.of(2026, 9, 4);
        for (int i = 0; i < closes.length; i++)
            candles.add(Candle.of(end.minusDays(closes.length - i - 1).plusDays(1).atStartOfDay(), closes[i]));
        return PriceSeriesThumbnail.of(CandleSeries.of(SymbolResource.of("TEST", TimeFrame.Period.DAILY), candles), 10);
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        EventQueue.invokeAndWait(task);
        return task.get();
    }
}
