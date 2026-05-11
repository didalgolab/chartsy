package one.chartsy.charting;

import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.event.EventListenerList;

import one.chartsy.charting.event.LabelRendererPropertyEvent;
import one.chartsy.charting.internal.TextRenderer;
import one.chartsy.charting.internal.TextRendererParameters;
import one.chartsy.charting.util.Dimension2DExt;
import one.chartsy.charting.util.GraphicUtil;
import one.chartsy.charting.util.java2d.ContrastingColor;
import one.chartsy.charting.util.java2d.ShapeUtil;
import one.chartsy.charting.util.text.BidiUtil;

/// Measures and paints centered text labels for scales, annotations, legends, and chart overlays.
///
/// A renderer instance stores the presentation state for a label, including foreground color,
/// optional background paint, border, explicit font, rotation, and wrapping settings. Measurement
/// methods and [#paintLabel(JComponent, Color, Graphics, String, double, double)] translate that
/// state into a prepared [TextRenderer] centered on the supplied anchor point.
///
/// When caching is enabled, prepared [TextRenderer] instances are kept behind a [SoftReference] and
/// reused by layout-affecting state such as text, font, antialiasing, wrapping, and rotation.
/// Instances are mutable and not thread-safe. Measurement and paint methods synchronize while they
/// reuse transient renderer state, but configuration changes and listener management are not
/// coordinated for concurrent use.
///
/// Registered listeners receive [LabelRendererPropertyEvent] instances whose effect flags say
/// whether a change affects drawing only or also the measured bounds. Owners such as `Scale`,
/// `ScaleAnnotation`, and `DataIndicator` use those flags to decide whether to repaint or fully
/// recompute geometry.
public class LabelRenderer implements Serializable, SwingConstants {
    private Font font;
    private boolean scalingFont;
    private Color foregroundColor;
    private final PlotStyle backgroundStyle;
    private boolean backgroundPaintSet;
    private double rotation;
    private boolean opaque;
    private boolean outline;
    private int alignment;
    private boolean autoWrapping;
    private float wrappingWidth;
    private Border border;
    private boolean cachingEnabled;
    private transient SoftReference<HashMap<TextRendererParameters, TextRenderer>> rendererCacheReference;
    private transient TextRendererParameters cacheProbe;
    private transient TextRenderer preparedRenderer;
    private EventListenerList listenerList;

    /// Creates a renderer that inherits its font and foreground from the target component.
    ///
    /// The initial configuration uses centered text, no explicit background paint, no border, zero
    /// rotation, wrapping disabled, and caching enabled.
    public LabelRenderer() {
        scalingFont = true;
        alignment = CENTER;
        cachingEnabled = true;
        initializeTransientState();
        backgroundStyle = new PlotStyle(Color.white);
        backgroundStyle.setAbsolutePaint(false);
    }

    /// Creates an opaque solid-background label style with a one-pixel border and horizontal
    /// padding.
    ///
    /// The supplied `borderColor` is used both for the border line and as the initial text color.
    ///
    /// @param background  solid background fill to use when the label is opaque
    /// @param borderColor border color and initial foreground color
    public LabelRenderer(Color background, Color borderColor) {
        this();
        border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor),
                BorderFactory.createEmptyBorder(0, 2, 0, 2));
        opaque = true;
        foregroundColor = borderColor;
        setBackground(background);
    }

    /// Returns a package-local fallback font when the target component has none.
    ///
    /// The default implementation returns `null`, which lets [TextRenderer] fall back to the
    /// current Swing `Label.font`.
    Font getFallbackFont() {
        return null;
    }

    private Font resolveFont(Component c) {
        if (font == null) {
            Font componentFont = c.getFont();
            return (componentFont != null) ? componentFont : getFallbackFont();
        }

        if (!scalingFont) {
            return font;
        }

        ScalableFontManager fontManager = ScalableFontManager.getFontManager(c);
        return (fontManager != null) ? fontManager.getDeriveFont(font) : font;
    }

    private boolean isTextAntiAliased(JComponent c) {
        if (c instanceof Chart.Area chartArea) {
            return chartArea.getChart().isAntiAliasingText();
        }
        if (c instanceof Legend legend) {
            return legend.isAntiAliasingText();
        }
        return c.getClientProperty("_AA_") != null;
    }

    private void prepareRenderer(JComponent c, String text) {
        Font resolvedFont = resolveFont(c);
        boolean antiAliased = isTextAntiAliased(c);

        if (!cachingEnabled) {
            preparedRenderer.setText(text);
            preparedRenderer.setFont(resolvedFont);
            preparedRenderer.setAntiAliased(antiAliased);
            preparedRenderer.setAutoWrapping(autoWrapping);
            preparedRenderer.setWrappingWidth(wrappingWidth);
            preparedRenderer.setRotation(rotation);
        } else {
            cacheProbe.setText(text);
            cacheProbe.setFont(resolvedFont);
            cacheProbe.setAntiAliased(antiAliased);
            cacheProbe.setAutoWrapping(autoWrapping);
            cacheProbe.setWrappingWidth(wrappingWidth);
            cacheProbe.setRotation(rotation);

            HashMap<TextRendererParameters, TextRenderer> rendererCache = rendererCacheReference.get();
            if (rendererCache == null) {
                rendererCache = new HashMap<>();
                rendererCacheReference = new SoftReference<>(rendererCache);
            }

            preparedRenderer = rendererCache.get(cacheProbe);
            if (preparedRenderer == null) {
                preparedRenderer = new TextRenderer();
                preparedRenderer.setText(text);
                preparedRenderer.setFont(resolvedFont);
                preparedRenderer.setAntiAliased(antiAliased);
                preparedRenderer.setAutoWrapping(autoWrapping);
                preparedRenderer.setWrappingWidth(wrappingWidth);
                preparedRenderer.setRotation(rotation);
                rendererCache.put(preparedRenderer, preparedRenderer);
            }
        }

        preparedRenderer.setOutline(outline);
        preparedRenderer.setAlignment(alignment);
    }

    private void initializeTransientState() {
        if (cachingEnabled) {
            rendererCacheReference = new SoftReference<>(new HashMap<>());
            cacheProbe = new TextRendererParameters();
            preparedRenderer = null;
        } else {
            rendererCacheReference = null;
            cacheProbe = null;
            preparedRenderer = new TextRenderer();
        }
    }

    private Insets getBorderInsets(JComponent c) {
        return (border != null) ? border.getBorderInsets(c) : null;
    }

    /// Registers a listener for renderer state changes.
    ///
    /// Listeners receive [LabelRendererPropertyEvent] instances, not plain
    /// `PropertyChangeEvent` values.
    ///
    /// @param listener listener to add
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        if (listenerList == null) {
            listenerList = new EventListenerList();
        }
        listenerList.add(PropertyChangeListener.class, listener);
    }

    /// Fires a [LabelRendererPropertyEvent] and then invokes [#stateChanged()].
    ///
    /// Callers that mutate renderer state outside the built-in setters can use this hook to keep
    /// owner caches and repaint behavior consistent with the standard property setters.
    ///
    /// @param propertyName event property name
    /// @param oldValue     previous value
    /// @param newValue     new value
    /// @param effects      bitmask composed from {@link LabelRendererPropertyEvent#AFFECTS_DRAWING} and
    ///                         {@link LabelRendererPropertyEvent#AFFECTS_BOUNDS}
    public void firePropertyChange(String propertyName, Object oldValue, Object newValue, int effects) {
        if (listenerList != null) {
            Object[] listeners = listenerList.getListenerList();
            if (listeners.length > 0) {
                LabelRendererPropertyEvent event =
                        new LabelRendererPropertyEvent(this, propertyName, oldValue, newValue, effects);
                for (int i = listeners.length - 1; i >= 1; i -= 2) {
                    ((PropertyChangeListener) listeners[i]).propertyChange(event);
                }
            }
        }
        stateChanged();
    }

    /// Returns the alignment used for multiline text blocks.
    ///
    /// Single-line labels remain centered around the anchor point regardless of this setting.
    ///
    /// @return multiline alignment passed through to the prepared [TextRenderer]
    public final int getAlignment() {
        return alignment;
    }

    /// Returns the explicit solid background color, if any.
    ///
    /// This method returns `null` when no explicit background paint is configured or when the
    /// configured background uses a non-[Color] [Paint].
    ///
    /// @return explicit solid background color, or `null`
    public final Color getBackground() {
        return backgroundPaintSet ? backgroundStyle.getFillColor() : null;
    }

    /// Returns the explicit background paint configured for this renderer.
    ///
    /// A `null` result means that the renderer has no fixed background paint of its own. If the
    /// label is still opaque, [#paintLabel(JComponent, Color, Graphics, String, double, double)]
    /// falls back to the target component's current background color at paint time.
    ///
    /// @return explicit background paint, or `null`
    public final Paint getBackgroundPaint() {
        return backgroundPaintSet ? backgroundStyle.getFillPaint() : null;
    }

    /// Returns the translated text-background shape centered on the supplied anchor point.
    ///
    /// The returned shape follows the prepared text block itself and does not include any configured
    /// border insets. Callers typically use this for hit testing or decoration geometry rather than
    /// for the opaque fill painted by [#paintLabel(JComponent, Color, Graphics, String, double,
    /// double)], which always uses a rectangle.
    ///
    /// @param c       target component that supplies font and antialiasing context
    /// @param centerX x-coordinate of the label center
    /// @param centerY y-coordinate of the label center
    /// @param text    label text
    /// @return translated background shape, or `null` when no shape is available
    public Shape getBackgroundShape(JComponent c, double centerX, double centerY, String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        Shape backgroundShape;
        synchronized (this) {
            prepareRenderer(c, text);
            backgroundShape = preparedRenderer.getBackgroundShape(true);
        }
        return (backgroundShape != null) ? ShapeUtil.getTranslatedShape(backgroundShape, centerX, centerY) : null;
    }

    /// Returns the optional border painted around the label bounds.
    ///
    /// The border contributes to [#getBounds(JComponent, double, double, String, Rectangle2D)],
    /// [#getSize(JComponent, String, boolean, boolean)], and
    /// [#getSize2D(JComponent, String, boolean, boolean)] when border inclusion is requested.
    ///
    /// @return configured border, or `null`
    public final Border getBorder() {
        return border;
    }

    /// Returns the axis-aligned paint bounds centered on the supplied anchor point.
    ///
    /// The returned bounds include configured border insets. Strings that become empty after
    /// stripping bidi control marks are treated as having no visible footprint.
    ///
    /// @param c       target component that supplies font and antialiasing context
    /// @param centerX x-coordinate of the label center
    /// @param centerY y-coordinate of the label center
    /// @param text    label text
    /// @param bounds  reusable rectangle to populate, or `null` to allocate a new one
    /// @return centered axis-aligned bounds for the label
    public Rectangle2D getBounds(JComponent c, double centerX, double centerY, String text, Rectangle2D bounds) {
        if (text == null || text.isEmpty()) {
            return new Rectangle2D.Double();
        }

        String visibleText = BidiUtil.removeMarksFromString(text);
        if (visibleText.isEmpty()) {
            return new Rectangle2D.Double();
        }

        double width;
        double height;
        synchronized (this) {
            prepareRenderer(c, text);
            Rectangle2D rendererBounds = preparedRenderer.getBounds(true);
            width = rendererBounds.getWidth();
            height = rendererBounds.getHeight();
        }

        Insets insets = getBorderInsets(c);
        if (insets != null) {
            width += insets.left + insets.right;
            height += insets.top + insets.bottom;
        }

        Rectangle2D result = (bounds != null) ? bounds : new Rectangle2D.Double();
        result.setRect(centerX - width / 2.0, centerY - height / 2.0, width, height);
        return result;
    }

    /// Returns the preferred text color, or `null` to inherit from the target component.
    ///
    /// @return configured text color, or `null`
    public final Color getColor() {
        return foregroundColor;
    }

    /// Returns the explicit font configured for this renderer.
    ///
    /// A `null` result means that measurement and painting still inherit from the target component
    /// and, if needed, from [#getFallbackFont()].
    ///
    /// @return explicit font, or `null`
    public final Font getFont() {
        return font;
    }

    /// Returns the current text rotation angle in degrees.
    ///
    /// @return rotation angle in degrees
    public final double getRotation() {
        return rotation;
    }

    /// Returns the measured label size rounded up to whole pixels.
    ///
    /// Strings that contain only bidi control marks are treated as empty. Border insets are
    /// included only when `includeBorder` is `true`.
    ///
    /// @param c             target component that supplies font and antialiasing context
    /// @param text          label text
    /// @param includeBorder whether to add border insets to the measured size
    /// @param rotated       whether to measure the axis-aligned bounds after rotation
    /// @return integer-sized label footprint
    public Dimension getSize(JComponent c, String text, boolean includeBorder, boolean rotated) {
        if (text == null || text.isEmpty()) {
            return new Dimension();
        }

        String visibleText = BidiUtil.removeMarksFromString(text);
        if (visibleText.isEmpty()) {
            return new Dimension();
        }

        double width;
        double height;
        synchronized (this) {
            prepareRenderer(c, text);
            Rectangle2D rendererBounds = preparedRenderer.getBounds(rotated);
            width = rendererBounds.getWidth();
            height = rendererBounds.getHeight();
        }

        if (includeBorder) {
            Insets insets = getBorderInsets(c);
            if (insets != null) {
                width += insets.left + insets.right;
                height += insets.top + insets.bottom;
            }
        }

        return new Dimension((int) Math.ceil(width), (int) Math.ceil(height));
    }

    /// Returns the measured label size without rounding to integer pixels.
    ///
    /// This method mirrors [#getSize(JComponent, String, boolean, boolean)] but preserves the
    /// sub-pixel bounds reported by the underlying [TextRenderer].
    ///
    /// @param c             target component that supplies font and antialiasing context
    /// @param text          label text
    /// @param includeBorder whether to add border insets to the measured size
    /// @param rotated       whether to measure the axis-aligned bounds after rotation
    /// @return floating-point label footprint
    public Dimension2D getSize2D(JComponent c, String text, boolean includeBorder, boolean rotated) {
        if (text == null || text.isEmpty()) {
            return new Dimension2DExt();
        }

        String visibleText = BidiUtil.removeMarksFromString(text);
        if (visibleText.isEmpty()) {
            return new Dimension2DExt();
        }

        double width;
        double height;
        synchronized (this) {
            prepareRenderer(c, text);
            Rectangle2D rendererBounds = preparedRenderer.getBounds(rotated);
            width = rendererBounds.getWidth();
            height = rendererBounds.getHeight();
        }

        if (includeBorder) {
            Insets insets = getBorderInsets(c);
            if (insets != null) {
                width += insets.left + insets.right;
                height += insets.top + insets.bottom;
            }
        }

        return new Dimension2DExt(width, height);
    }

    /// Returns the configured maximum line width for automatic wrapping.
    ///
    /// Non-positive values disable width-based wrapping even when [#isAutoWrapping()] is `true`.
    ///
    /// @return configured wrapping width
    public final float getWrappingWidth() {
        return wrappingWidth;
    }

    /// Returns whether automatic line breaking is enabled.
    ///
    /// Automatic wrapping has no effect until [#setWrappingWidth(float)] supplies a positive width.
    ///
    /// @return `true` when automatic wrapping is enabled
    public final boolean isAutoWrapping() {
        return autoWrapping;
    }

    /// Returns whether layout preparation uses a soft-referenced [TextRenderer] cache.
    ///
    /// When this is `false`, the renderer reuses a single mutable [TextRenderer] instance instead.
    ///
    /// @return `true` when prepared renderers are cached
    public final boolean isCaching() {
        return cachingEnabled;
    }

    /// Returns whether label painting fills a rectangular background before drawing text.
    ///
    /// Borders are painted independently and may still appear even when this flag is `false`.
    ///
    /// @return `true` when paint operations fill the background rectangle
    public final boolean isOpaque() {
        return opaque;
    }

    /// Returns whether prepared text is drawn with the [TextRenderer] outline halo.
    ///
    /// @return `true` when outline painting is enabled
    public final boolean isOutline() {
        return outline;
    }

    /// Returns whether explicit fonts are passed through [ScalableFontManager].
    ///
    /// The flag matters only when [#getFont()] returns a non-`null` explicit font.
    ///
    /// @return `true` when explicit fonts are scaled through [ScalableFontManager]
    public final boolean isScalingFont() {
        return scalingFont;
    }

    /// Paints `text` centered on the supplied anchor point.
    ///
    /// When the label is opaque or has a border, the paint bounds are computed first so the
    /// background fill and border can use the same rectangle. A `null` `colorOverride` falls back
    /// to [#getColor()] and then to the target component's foreground color. When the chosen text
    /// color is a [ContrastingColor], this method resolves it against either the explicit background
    /// paint, the {@link ContrastingColor#KNOWN_BACKGROUND_COLOR} rendering hint, or, as a last
    /// resort, by temporarily using that color as the active composite.
    ///
    /// @param c             target component that supplies font and antialiasing context
    /// @param colorOverride foreground color to use for this draw call, or `null` to use the
    ///                          renderer's configured foreground resolution
    /// @param g             target graphics
    /// @param text          label text
    /// @param centerX       x-coordinate of the label center
    /// @param centerY       y-coordinate of the label center
    public void paintLabel(JComponent c, Color colorOverride, Graphics g, String text, double centerX, double centerY) {
        if (text == null || text.isEmpty()) {
            return;
        }

        synchronized (this) {
            Rectangle bounds = null;
            if (opaque || border != null) {
                bounds = GraphicUtil.toRectangle(getBounds(c, centerX, centerY, text, null), null);
            } else {
                prepareRenderer(c, text);
            }

            Color originalColor = g.getColor();
            Font originalFont = g.getFont();
            try {
                if (opaque && bounds != null) {
                    if (!backgroundPaintSet) {
                        backgroundStyle.setFillPaintInternal(c.getBackground());
                    }
                    backgroundStyle.fillRect(g, bounds.x, bounds.y, bounds.width, bounds.height);
                }

                boolean drawnByComposite = false;
                Color resolvedColor = (colorOverride != null)
                        ? colorOverride
                        : (foregroundColor != null ? foregroundColor : c.getForeground());
                Color effectiveColor = resolvedColor;
                if (resolvedColor instanceof ContrastingColor contrastingColor) {
                    Graphics2D g2 = (Graphics2D) g;
                    Color knownBackground = opaque
                            ? backgroundStyle.getFillColor()
                            : (Color) g2.getRenderingHint(ContrastingColor.KNOWN_BACKGROUND_COLOR);
                    if (knownBackground != null) {
                        effectiveColor = contrastingColor.chooseColor(knownBackground);
                    } else {
                        Composite originalComposite = g2.getComposite();
                        try {
                            g2.setComposite(contrastingColor);
                            preparedRenderer.draw(g2, Color.black, null, centerX, centerY);
                            drawnByComposite = true;
                        } finally {
                            g2.setComposite(originalComposite);
                        }
                    }
                }

                if (!drawnByComposite) {
                    preparedRenderer.draw(g, effectiveColor, null, centerX, centerY);
                }
                if (border != null && bounds != null) {
                    border.paintBorder(c, g, bounds.x, bounds.y, bounds.width, bounds.height);
                }
            } finally {
                g.setColor(originalColor);
                g.setFont(originalFont);
            }
        }
    }

    /// Equivalent to [#paintLabel(JComponent, Color, Graphics, String, double, double)] with a
    /// `null` color override.
    ///
    /// @param c target component that supplies font and antialiasing context
    /// @param g target graphics
    /// @param text label text
    /// @param centerX x-coordinate of the label center
    /// @param centerY y-coordinate of the label center
    public void paintLabel(JComponent c, Graphics g, String text, double centerX, double centerY) {
        paintLabel(c, null, g, text, centerX, centerY);
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initializeTransientState();
    }

    /// Unregisters a listener previously added through [#addPropertyChangeListener(PropertyChangeListener)].
    ///
    /// @param listener listener to remove
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        if (listenerList != null) {
            listenerList.remove(PropertyChangeListener.class, listener);
        }
    }

    /// Sets the alignment used for multiline labels.
    ///
    /// Changing alignment always affects drawing. It affects measured bounds as well when the label
    /// is rotated.
    ///
    /// @param alignment one of the `SwingConstants` horizontal alignment values used by
    ///                      [TextRenderer]
    public void setAlignment(int alignment) {
        int oldAlignment = this.alignment;
        if (alignment != oldAlignment) {
            this.alignment = alignment;
            int effects = LabelRendererPropertyEvent.AFFECTS_DRAWING
                    | (rotation == 0.0 ? 0 : LabelRendererPropertyEvent.AFFECTS_BOUNDS);
            firePropertyChange("alignment", oldAlignment, alignment, effects);
        }
    }

    /// Enables or disables automatic line breaking.
    ///
    /// The flag has no visual effect until [#setWrappingWidth(float)] supplies a positive width.
    ///
    /// @param autoWrapping `true` to enable automatic wrapping
    public void setAutoWrapping(boolean autoWrapping) {
        boolean oldAutoWrapping = this.autoWrapping;
        if (autoWrapping != oldAutoWrapping) {
            this.autoWrapping = autoWrapping;
            firePropertyChange(
                    "autoWrapping",
                    oldAutoWrapping,
                    autoWrapping,
                    LabelRendererPropertyEvent.AFFECTS_DRAWING | LabelRendererPropertyEvent.AFFECTS_BOUNDS);
        }
    }

    /// Convenience overload for [#setBackgroundPaint(Paint)] when using a solid [Color].
    ///
    /// @param background solid background fill, or `null` to clear the explicit background
    public void setBackground(Color background) {
        setBackgroundPaint(background);
    }

    /// Sets the explicit background paint used when this renderer is opaque.
    ///
    /// Passing `null` clears the renderer's own background paint. If the renderer remains opaque,
    /// later paint calls fall back to the target component's current background color.
    ///
    /// @param backgroundPaint explicit background paint, or `null`
    public void setBackgroundPaint(Paint backgroundPaint) {
        Paint oldBackgroundPaint = getBackgroundPaint();
        if (backgroundPaint != oldBackgroundPaint) {
            if (backgroundPaint == null) {
                backgroundPaintSet = false;
            } else {
                backgroundPaintSet = true;
                backgroundStyle.setFillPaintInternal(backgroundPaint);
            }
            assert getBackgroundPaint() == backgroundPaint;
            firePropertyChange(
                    "backgroundPaint",
                    oldBackgroundPaint,
                    backgroundPaint,
                    LabelRendererPropertyEvent.AFFECTS_DRAWING);
        }
    }

    /// Sets the border painted around the label bounds.
    ///
    /// Borders contribute to measured bounds and are painted even when [#isOpaque()] is `false`.
    ///
    /// @param border border to paint around the label, or `null`
    public void setBorder(Border border) {
        Border oldBorder = this.border;
        if (!Objects.equals(border, oldBorder)) {
            this.border = border;
            firePropertyChange(
                    "border",
                    oldBorder,
                    border,
                    LabelRendererPropertyEvent.AFFECTS_DRAWING | LabelRendererPropertyEvent.AFFECTS_BOUNDS);
        }
    }

    /// Enables or disables [TextRenderer] caching for repeated measurement and paint preparation.
    ///
    /// Switching cache mode rebuilds the renderer's transient state but does not by itself change
    /// the current visual result.
    ///
    /// @param caching `true` to enable the soft-referenced renderer cache
    public void setCaching(boolean caching) {
        boolean oldCaching = cachingEnabled;
        if (caching != oldCaching) {
            synchronized (this) {
                cachingEnabled = caching;
                initializeTransientState();
            }
            firePropertyChange("caching", oldCaching, caching, 0);
        }
    }

    /// Sets the preferred text color for future paint operations.
    ///
    /// Passing `null` restores inheritance from the target component's foreground color.
    ///
    /// @param color preferred text color, or `null` to inherit from the component
    public void setColor(Color color) {
        Color oldColor = foregroundColor;
        if (color != oldColor) {
            foregroundColor = color;
            firePropertyChange("color", oldColor, color, LabelRendererPropertyEvent.AFFECTS_DRAWING);
        }
    }

    /// Sets the explicit font for future measurement and paint operations.
    ///
    /// Passing `null` restores inheritance from the target component and [#getFallbackFont()].
    ///
    /// @param font explicit font, or `null` to inherit from the component
    public void setFont(Font font) {
        Font oldFont = this.font;
        if (font != oldFont) {
            this.font = font;
            firePropertyChange(
                    "font",
                    oldFont,
                    font,
                    LabelRendererPropertyEvent.AFFECTS_DRAWING | LabelRendererPropertyEvent.AFFECTS_BOUNDS);
        }
    }

    /// Enables or disables rectangular background filling.
    ///
    /// This flag controls only the fill pass. Borders still paint when a border is configured.
    ///
    /// @param opaque `true` to fill the background rectangle before drawing text
    public void setOpaque(boolean opaque) {
        boolean oldOpaque = this.opaque;
        if (opaque != oldOpaque) {
            this.opaque = opaque;
            firePropertyChange("opaque", oldOpaque, opaque, LabelRendererPropertyEvent.AFFECTS_DRAWING);
        }
    }

    /// Enables or disables the [TextRenderer] outline halo.
    ///
    /// Outlining changes only the paint result, not the measured bounds.
    ///
    /// @param outline `true` to paint the outline halo
    public void setOutline(boolean outline) {
        boolean oldOutline = this.outline;
        if (outline != oldOutline) {
            this.outline = outline;
            firePropertyChange("outline", oldOutline, outline, LabelRendererPropertyEvent.AFFECTS_DRAWING);
        }
    }

    /// Sets the text rotation angle in degrees.
    ///
    /// @param rotation rotation angle in degrees
    public void setRotation(double rotation) {
        double oldRotation = this.rotation;
        if (rotation != oldRotation) {
            this.rotation = rotation;
            firePropertyChange(
                    "rotation",
                    oldRotation,
                    rotation,
                    LabelRendererPropertyEvent.AFFECTS_DRAWING | LabelRendererPropertyEvent.AFFECTS_BOUNDS);
        }
    }

    /// Enables or disables font scaling through [ScalableFontManager].
    ///
    /// The flag matters only when an explicit font has been configured through [#setFont(Font)].
    ///
    /// @param scalingFont `true` to scale explicit fonts through [ScalableFontManager]
    public void setScalingFont(boolean scalingFont) {
        boolean oldScalingFont = this.scalingFont;
        if (scalingFont != oldScalingFont) {
            this.scalingFont = scalingFont;
            firePropertyChange(
                    "scalingFont",
                    oldScalingFont,
                    scalingFont,
                    LabelRendererPropertyEvent.AFFECTS_DRAWING | LabelRendererPropertyEvent.AFFECTS_BOUNDS);
        }
    }

    /// Sets the maximum line width used by the auto-wrapping algorithm.
    ///
    /// Non-positive values disable width-based wrapping even when [#isAutoWrapping()] is `true`.
    ///
    /// @param wrappingWidth maximum line width used by automatic wrapping
    public void setWrappingWidth(float wrappingWidth) {
        float oldWrappingWidth = this.wrappingWidth;
        if (wrappingWidth != oldWrappingWidth) {
            this.wrappingWidth = wrappingWidth;
            firePropertyChange(
                    "wrappingWidth",
                    oldWrappingWidth,
                    wrappingWidth,
                    LabelRendererPropertyEvent.AFFECTS_DRAWING | LabelRendererPropertyEvent.AFFECTS_BOUNDS);
        }
    }

    /// Hook invoked after every successful [#firePropertyChange(String, Object, Object, int)] call.
    ///
    /// Subclasses use this to invalidate owner-side caches that are not expressed through ordinary
    /// property-change listeners.
    protected void stateChanged() {
    }
}
