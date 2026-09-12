package one.chartsy.exploration.ui;

import one.chartsy.misc.PriceSeriesThumbnail;
import one.chartsy.misc.StyledValue;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JToolTip;
import javax.swing.UIManager;
import javax.swing.table.TableCellRenderer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Line2D;
import java.awt.geom.Ellipse2D;
import java.text.DecimalFormat;
import java.util.Locale;

/** One reusable vector renderer; snapshots contain only the already-loaded price window. */
final class PriceThumbnailRenderer extends JLabel implements TableCellRenderer {
    static final int WIDTH = 156;
    static final int HEIGHT = 30;
    private static final DecimalFormat PRICE = new DecimalFormat("#,##0.00##");
    private PriceSeriesThumbnail thumbnail;
    private Color lineColor;

    static PriceSeriesThumbnail thumbnail(Object value) {
        return value instanceof StyledValue styled && styled.value() instanceof PriceSeriesThumbnail prices
                ? prices : null;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                   boolean focused, int row, int column) {
        thumbnail = thumbnail(value);
        setOpaque(true);
        setBackground(selected ? table.getSelectionBackground() : table.getBackground());
        setForeground(selected ? table.getSelectionForeground() : table.getForeground());
        var trend = thumbnail == null ? PriceThumbnailTrend.UNAVAILABLE : PriceThumbnailTrend.of(thumbnail);
        lineColor = trend.colorOn(getBackground());
        setFont(table.getFont());
        setBorder(focused ? UIManager.getBorder("Table.focusCellHighlightBorder") : null);
        setText("");
        getAccessibleContext().setAccessibleName(thumbnail == null ? "No price history"
                : trend.label + "; " + thumbnail + "; Space to preview, double-click to open chart");
        return this;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(WIDTH, HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (thumbnail == null)
            return;
        var g = (Graphics2D) graphics.create();
        try {
            paintChart(g, thumbnail, 6, 4, getWidth() - 12, getHeight() - 9, lineColor);
        } finally {
            g.dispose();
        }
    }

    static String changeText(PriceSeriesThumbnail prices) {
        return Double.isFinite(prices.changePercent())
                ? String.format(Locale.ROOT, "%+.1f%%", prices.changePercent()) : "—";
    }

    static String previewSummary(PriceSeriesThumbnail prices) {
        return PriceThumbnailTrend.of(prices).label + " · " + prices;
    }

    static void paintChart(Graphics2D graphics, PriceSeriesThumbnail prices,
                           int x, int y, int width, int height, Color color) {
        if (prices.size() == 0 || !Double.isFinite(prices.min()) || width < 2 || height < 2)
            return;
        var g = (Graphics2D) graphics.create();
        try {
            g.clipRect(x - 3, y - 3, width + 6, height + 6);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            var transform = g.getTransform();
            double scale = Math.hypot(transform.getScaleY(), transform.getShearX());
            double deviceStroke = 1.5 * Math.max(1, Math.round(.8 * scale));
            float stroke = (float) (deviceStroke / scale);
            double actualRange = prices.max() - prices.min();
            double reference = (prices.max() + prices.min()) / 2;
            double range = Math.max(actualRange, Math.abs(reference) * .05);
            double upper = reference + range / 2;
            long first = prices.timeAt(0);
            long duration = prices.timeAt(prices.size() - 1) - first;
            var path = new Path2D.Double();
            boolean connected = false;
            double lastX = 0, lastY = 0;
            boolean lastValid = false;
            for (int i = 0; i < prices.size(); i++) {
                double close = prices.closeAt(i);
                if (!Double.isFinite(close)) {
                    connected = false;
                    lastValid = false;
                    continue;
                }
                double px = x + (duration == 0 ? width / 2.0 : width * ((double) (prices.timeAt(i) - first) / duration));
                double py = y + (range == 0 ? height / 2.0 : height * ((upper - close) / range));
                if (actualRange == 0) {
                    // Align one stroke edge to the device grid, including half-pixel widths.
                    double phase = (deviceStroke / 2) % 1;
                    py = (Math.rint(py * scale + transform.getTranslateY() - phase)
                            + phase - transform.getTranslateY()) / scale;
                }
                if (connected)
                    path.lineTo(px, py);
                else
                    path.moveTo(px, py);
                connected = true;
                lastValid = true;
                lastX = px;
                lastY = py;
            }
            // The first close is a quiet reference, not a zero-price axis.
            if (Double.isFinite(prices.closeAt(0))) {
                double baseline = y + (range == 0 ? height / 2.0 : height * ((upper - prices.closeAt(0)) / range));
                baseline = (Math.floor(baseline * scale + transform.getTranslateY()) + .5 - transform.getTranslateY()) / scale;
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 55));
                g.setStroke(new BasicStroke((float) (1 / scale)));
                g.draw(new Line2D.Double(x, baseline, x + width, baseline));
            }
            g.setColor(color);
            g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(path);
            if (lastValid)
                g.fill(new Ellipse2D.Double(lastX - 1.8, lastY - 1.8, 3.6, 3.6));
        } finally {
            g.dispose();
        }
    }

    static JToolTip preview(PriceSeriesThumbnail prices, String symbol) {
        return new JToolTip() {
            @Override
            public Dimension getPreferredSize() {
                int lineHeight = getFontMetrics(getFont()).getHeight();
                return new Dimension(400, 152 + 6 * lineHeight);
            }

            @Override
            protected void paintComponent(Graphics graphics) {
                var g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g.setColor(getBackground());
                    g.fillRect(0, 0, getWidth(), getHeight());
                    g.setColor(getForeground());
                    g.setFont(getFont());
                    int line = g.getFontMetrics().getHeight();
                    var trend = PriceThumbnailTrend.of(prices);
                    Color ink = trend.colorOn(getBackground());
                    g.drawString(symbol + " · daily close · " + changeText(prices), 12, line + 4);
                    g.setColor(ink);
                    g.drawString(trend.label, 12, 2 * line + 4);
                    g.setColor(getForeground());
                    g.drawString(prices.startDate() + " — " + prices.endDate(), 12, 3 * line + 4);
                    paintChart(g, prices, 14, 3 * line + 16, getWidth() - 28, 110, ink);
                    g.setColor(getForeground());
                    if (prices.size() > 0)
                        g.drawString("Close " + PRICE.format(prices.closeAt(0)) + " → "
                                + PRICE.format(prices.closeAt(prices.size() - 1)) + "   ·   Range "
                                + PRICE.format(prices.min()) + "–" + PRICE.format(prices.max()), 12, getHeight() - 2 * line - 6);
                    g.setFont(getFont().deriveFont(Math.max(10f, getFont().getSize2D() - 1)));
                    g.drawString("Strong: ≥15% move and ≥60% of range · Flat: ≤5% range", 12, getHeight() - line - 5);
                    g.drawString("Space: preview · Esc: close · Double-click cell: chart", 12, getHeight() - 5);
                } finally {
                    g.dispose();
                }
            }
        };
    }
}
