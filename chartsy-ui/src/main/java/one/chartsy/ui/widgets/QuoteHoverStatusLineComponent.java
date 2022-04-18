package one.chartsy.ui.widgets;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.Serial;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.TimeFrame;
import one.chartsy.TimeFrameHelper;
import one.chartsy.ui.chart.ChartData;
import one.chartsy.ui.chart.ChartFrame;
import one.chartsy.ui.chart.hover.HoverEvent;
import one.chartsy.ui.chart.hover.QuoteHoverListener;
import org.openide.awt.StatusLineElementProvider;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;

@ServiceProviders({
    @ServiceProvider(service = StatusLineElementProvider.class),
    @ServiceProvider(service = QuoteHoverListener.class) })
public class QuoteHoverStatusLineComponent extends JComponent
        implements StatusLineElementProvider, QuoteHoverListener {
    @Serial
    private static final long serialVersionUID = -4838549963376979243L;
    /** The hovered quote data. */
    private Candle candle;
    /** Indicates whether the source chart time frame is intraday. */
    private boolean intraday;
    /** The decimal format used to display bar numeric data. */
    private final NumberFormat numberFormat = new DecimalFormat("0.00###");
    /** The pure date format used to display data. */
    private final DateTimeFormatter eodDateFormat = DateTimeFormatter.ofPattern("O  yyyy-MM-dd");
    /** The date&time format used to display data. */
    private final DateTimeFormatter intradayDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** The default time zone used to display intraday date and time. */
    private final ZoneId displayTimeZone = ZoneId.systemDefault();
    /** The default time zone used to display daily date. */
    private ZoneId dailyAlignmentTimeZone;
    /** The current component preferred size. */
    private final Dimension preferredSize = new Dimension(1, 1);
    
    public QuoteHoverStatusLineComponent() {
        updateUI();
    }
    
    @Override
    public void mouseEntered(HoverEvent event) {
        Object source = event.getSource();
        this.candle = (Candle) event.getValue();
        if (source instanceof ChartFrame) {
            ChartData chartData = ((ChartFrame) source).getChartData();

            TimeFrame timeFrame = chartData.getTimeFrame();
            boolean isIntraday = this.intraday = TimeFrameHelper.isIntraday(timeFrame);
            if (!isIntraday) {
                SymbolIdentity symbol = chartData.getSymbol();
                //if (symbol.getProvider() != null)
                //    this.dailyAlignmentTimeZone = symbol.getProvider().getCandleAlignment(symbol).map(CandleAlignment::getDailyAlignmentTimeZone).orElse(null);
            }
        } else {
            this.intraday = false;
            this.dailyAlignmentTimeZone = null;
        }
        repaint();
    }
    
    @Override
    public void mouseExited(HoverEvent event) {
        this.candle = null;
    }
    
    public Font getStrongFont() {
        Font font = getFont();
        if (font != null) {
            font = font.deriveFont(font.getSize2D() * 1.25f);
        }
        return font;
    }
    
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(preferredSize);
    }
    
    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }
    
    private int prefDataColW, prefTextColW;
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (candle != null) {
            // paint from right to left
            String open = numberFormat.format(candle.open());
            String high = numberFormat.format(candle.high());
            String low = numberFormat.format(candle.low());
            String close = numberFormat.format(candle.close());
            String date;
            if (intraday)
                date = intradayDateFormat.format(candle.getDateTime(displayTimeZone));
            else if (dailyAlignmentTimeZone != null)
                date = eodDateFormat.format(candle.getDateTime(dailyAlignmentTimeZone));
            else
                date = DateTimeFormatter.ISO_LOCAL_DATE.format(candle.getDateTime());
            
            FontMetrics fm1 = getFontMetrics(getFont());
            FontMetrics fm2 = getFontMetrics(getStrongFont());
            int gap = fm2.getHeight() / 2;
            int openTextW = gap + fm1.stringWidth("Open"), openDataW = fm2.stringWidth(open);
            int highTextW = gap + fm1.stringWidth("High"), highDataW = fm2.stringWidth(high);
            int lowTextW = gap + fm1.stringWidth("Low"), lowDataW = fm2.stringWidth(low);
            int closeTextW = gap + fm1.stringWidth("Close"), closeDataW = fm2.stringWidth(close);
            int dateTextW = gap + fm1.stringWidth(date);
            int dataW = gap + Math.max(Math.max(openDataW, highDataW), Math.max(lowDataW, closeDataW));
            int textW = Math.max(Math.max(openTextW, highTextW), Math.max(lowTextW, closeTextW));
            if (dataW > prefDataColW || dataW < prefDataColW*2/3)
                prefDataColW = dataW;
            else
                dataW = prefDataColW;
            if (textW > prefTextColW || textW < prefTextColW*2/3)
                prefTextColW = textW;
            else
                textW = prefTextColW;
            
            // draw text labels
            int width = getWidth();
            int y = getHeight()/2 - fm1.getHeight()/2 + fm1.getAscent();
            g.drawString("Close", width - closeTextW - dataW, y);
            g.drawString("Low", width - lowTextW - textW - 2 * dataW, y);
            g.drawString("High", width - highTextW - 2 * textW - 3 * dataW, y);
            g.drawString("Open", width - openTextW - 3 * textW - 4 * dataW, y);
            g.drawString(date.concat(":"), width - dateTextW - 4 * textW - 4 * dataW, y);
            
            // draw data values
            y = getHeight()/2 - fm2.getHeight()/2 + fm2.getAscent() - 1;
            g.setFont(getStrongFont());
            g.drawString(close, width - dataW, y);
            g.drawString(low, width - textW - 2 * dataW, y);
            g.drawString(high, width - 2 * textW - 3 * dataW, y);
            g.drawString(open, width - 3 * textW - 4 * dataW, y);
            
            // draw extra line decorations over the high and under the low price
            y = getHeight() - 2;
            g.drawLine(width - highTextW - 2 * textW - 3 * dataW - 1, 0, width - 2 * textW - 3 * dataW + highDataW, 0);
            g.drawLine(width - lowTextW - textW - 2 * dataW - 1, y, width - textW - 2 * dataW + lowDataW, y);
            
            int prefWidth = gap + dateTextW + 4 * dataW + 4 * textW;
            if (prefWidth > preferredSize.width
                    || prefWidth < preferredSize.width * 2 / 3) {
                preferredSize.setSize(prefWidth, fm2.getHeight() + 1);
                SwingUtilities.invokeLater(() -> {
                    revalidate();
                    repaint();
                });
            }
        }
    }
    
    @Override
    public Component getStatusLineElement() {
        return this;
    }
}
