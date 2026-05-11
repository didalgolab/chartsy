package one.chartsy.charting.graphic;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.TexturePaint;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.imageio.ImageIO;

import one.chartsy.charting.Chart;

/// Decoration that paints one image either at an anchored plot position, tiled across the plot, or
/// stretched across the plot.
///
/// [#ANCHORED] uses the inherited anchor and offset from [PositionableDecoration]. [#TILED] and
/// [#SCALED] ignore those alignment settings and always start at the current plot rectangle's
/// top-left corner.
///
/// Texture paints are cached per [GraphicsConfiguration] so repeated paints do not recreate
/// compatible images on every frame. The cache is flushed when the source image publishes a new
/// animation frame or when tiled/scaled geometry changes.
///
/// ### API Note
///
/// Instances created from a [URL] reload their image after deserialization. Instances created from
/// an arbitrary [Image] keep that image only in memory; if serialized without a backing URL, they
/// deserialize without image content and therefore paint nothing.
public class ImageDecoration extends PositionableDecoration {

    /// Observes incremental or animated image updates and repaints the decoration accordingly.
    ///
    /// When the image starts producing frame updates, the enclosing decoration also invalidates any
    /// cached tiled or scaled texture paint so the next paint pass uses the newest frame.
    private final class ImageListener implements ImageObserver, Serializable {
        private boolean animated;

        /// Restarts animation callbacks after the decoration is attached to a chart again.
        void resumeAnimationUpdates() {
            if (image != null && animated)
                Toolkit.getDefaultToolkit().prepareImage(image, -1, -1, this);
        }

        /// Consumes toolkit image notifications for the retained source image.
        ///
        /// Error notifications drop the current image so later paints become no-ops. Full-image and
        /// frame notifications repaint the decoration, and animated frames also flush cached
        /// texture paints. Once an animated image is detached from a chart, the listener stops
        /// requesting further updates.
        @Override
        public boolean imageUpdate(Image updatedImage, int infoFlags, int x, int y, int width, int height) {
            if (updatedImage != image)
                return false;

            if ((infoFlags & ERROR) != 0) {
                image = null;
                repaint();
            }
            if ((infoFlags & ALLBITS) != 0)
                repaint();
            if ((infoFlags & FRAMEBITS) != 0) {
                animated = true;
                if (texturePaintCache != null)
                    flushTexturePaintCache();
                repaint();
            }
            if (animated && getChart() == null)
                return false;
            return (infoFlags & (ALLBITS | ERROR)) == 0;
        }
    }

    /// Paint the image once at the anchored location.
    public static final int ANCHORED = 0;
    /// Fill the plot rectangle by repeating the image as a texture tile.
    public static final int TILED = 1;
    /// Fill the plot rectangle by stretching one image tile across the whole plot.
    public static final int SCALED = 2;

    private transient Image image;
    private transient Map<GraphicsConfiguration, TexturePaint> texturePaintCache;
    private URL imageUrl;
    private int mode;
    private final ImageListener imageListener;

    /// Creates an anchored image decoration centered in the plot rectangle.
    ///
    /// @param image image to paint, retained by reference
    public ImageDecoration(Image image) {
        this(image, ANCHORED, CENTER);
    }

    /// Creates an image decoration from an in-memory image.
    ///
    /// The supplied image is retained by reference rather than copied. If `mode` is [#TILED] or
    /// [#SCALED], the inherited anchor and offset settings are ignored while painting.
    ///
    /// @param image  image to paint, retained by reference
    /// @param mode   one of [#ANCHORED], [#TILED], or [#SCALED]
    /// @param anchor inherited plot anchor used only in [#ANCHORED] mode
    /// @throws IllegalArgumentException if `mode` is invalid or `image` has no positive dimensions
    public ImageDecoration(Image image, int mode, int anchor) {
        super(anchor);
        imageListener = new ImageListener();
        initializeImage(image, mode);
    }

    /// Creates an image decoration backed by a reloadable URL.
    ///
    /// The image is loaded immediately and the URL is retained so deserialization can reload the
    /// content later.
    ///
    /// @param imageUrl URL used to load the image
    /// @param mode     one of [#ANCHORED], [#TILED], or [#SCALED]
    /// @param anchor   inherited plot anchor used only in [#ANCHORED] mode
    /// @throws IOException              if the image cannot be loaded from `imageUrl`
    /// @throws IllegalArgumentException if `mode` is invalid or the loaded image has no positive
    ///                                      dimensions
    public ImageDecoration(URL imageUrl, int mode, int anchor) throws IOException {
        super(anchor);
        imageListener = new ImageListener();
        this.imageUrl = imageUrl;
        initializeImage(loadImage(imageUrl), mode);
    }

    /// Reattaches image observation after the decoration is connected to a new chart.
    @Override
    protected void chartConnected(Chart previousChart, Chart chart) {
        super.chartConnected(previousChart, chart);
        if (chart != null)
            imageListener.resumeAnimationUpdates();
    }

    /// Recomputes the paint origin for the current image mode.
    ///
    /// Anchored mode delegates to the inherited anchor calculation. Tiled and scaled modes always
    /// align the image origin with the current plot rectangle's top-left corner and invalidate the
    /// texture-paint cache when that geometry changes.
    @Override
    protected void computeLocation() {
        if (mode == ANCHORED) {
            super.computeLocation();
            return;
        }

        Point location = getLocation();
        if (getChart() == null)
            location.setLocation(0, 0);
        else
            location.setLocation(getChart().getChartArea().getPlotRect().getLocation());

        if (texturePaintCache != null)
            flushTexturePaintCache();
    }

    /// Paints the retained image according to the current mode.
    ///
    /// The method becomes a no-op when no image content is currently available.
    @Override
    public void draw(Graphics g) {
        if (image == null || getChart() == null)
            return;

        Graphics2D g2 = (Graphics2D) g;
        Point location = getLocation();
        switch (mode) {
            case ANCHORED:
                g2.drawImage(image, location.x, location.y, imageListener);
                break;
            case TILED:
            case SCALED:
                Rectangle plotRect = getChart().getChartArea().getPlotRect();
                TexturePaint texturePaint = resolveTexturePaint(g2.getDeviceConfiguration(), plotRect, location);
                if (texturePaint == null)
                    return;

                Paint previousPaint = g2.getPaint();
                g2.setPaint(texturePaint);
                g2.fillRect(location.x, location.y, plotRect.width, plotRect.height);
                g2.setPaint(previousPaint);
                break;
            default:
                break;
        }
    }

    /// Returns the device-space bounds currently painted by this decoration.
    ///
    /// Detached decorations or decorations without image content report an empty rectangle.
    @Override
    public Rectangle2D getBounds(Rectangle2D bounds) {
        Rectangle2D result = (bounds != null) ? bounds : new Rectangle();
        if (getChart() == null || image == null) {
            result.setRect(0.0, 0.0, 0.0, 0.0);
            return result;
        }

        Point location = getLocation();
        switch (mode) {
            case ANCHORED:
                result.setRect(location.x, location.y, image.getWidth(imageListener), image.getHeight(imageListener));
                break;
            case TILED:
            case SCALED:
                result.setRect(getChart().getChartArea().getPlotRect());
                break;
            default:
                result.setRect(0.0, 0.0, 0.0, 0.0);
                break;
        }
        return result;
    }

    /// Returns the URL used to reload this decoration's image, if any.
    ///
    /// @return retained reload URL, or `null` when this instance was created from an in-memory
    ///     [Image]
    public final URL getImageURL() {
        return imageUrl;
    }

    /// Returns the active image placement mode.
    ///
    /// @return one of [#ANCHORED], [#TILED], or [#SCALED]
    public int getMode() {
        return mode;
    }

    /// Changes how the image is mapped onto the plot rectangle.
    ///
    /// If this decoration is currently attached and visible, the owning chart repaints the union of
    /// the old and new bounds so stale pixels are cleared.
    ///
    /// @param mode one of [#ANCHORED], [#TILED], or [#SCALED]
    /// @throws IllegalArgumentException if `mode` is invalid
    public void setMode(int mode) {
        if (this.mode == mode)
            return;

        validateMode(mode);
        Rectangle2D previousBounds = isAttachedAndVisible() ? getBounds(null) : null;
        this.mode = mode;
        computeLocation();
        if (previousBounds != null) {
            updateBoundsCache();
            Rectangle2D repaintBounds = getBounds(null);
            repaintBounds.add(previousBounds);
            getChart().getChartArea().repaint2D(repaintBounds);
        }
    }

    private void initializeImage(Image image, int mode) {
        validateMode(mode);
        validateImageDimensions(image);
        this.mode = mode;
        this.image = image;
        loadImageFromUrl();
    }

    private boolean isAttachedAndVisible() {
        return getChart() != null && isVisible();
    }

    private void loadImageFromUrl() {
        if (image == null && imageUrl != null) {
            try {
                image = loadImage(imageUrl);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static Image loadImage(URL imageUrl) throws IOException {
        Image image = ImageIO.read(imageUrl);
        if (image == null)
            throw new IOException("Unsupported image: " + imageUrl);
        validateImageDimensions(image);
        return image;
    }

    private synchronized TexturePaint resolveTexturePaint(GraphicsConfiguration configuration, Rectangle plotRect,
                                                          Point location) {
        if (image == null)
            return null;

        if (texturePaintCache == null)
            texturePaintCache = new HashMap<>();

        TexturePaint texturePaint = texturePaintCache.get(configuration);
        if (texturePaint == null) {
            BufferedImage compatibleImage = configuration.createCompatibleImage(
                    image.getWidth(imageListener),
                    image.getHeight(imageListener),
                    Transparency.TRANSLUCENT);
            Graphics2D imageGraphics = compatibleImage.createGraphics();
            if (mode == SCALED)
                imageGraphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            imageGraphics.drawImage(image, 0, 0, imageListener);
            imageGraphics.dispose();

            Rectangle anchorRectangle = (mode == TILED)
                    ? new Rectangle(location.x, location.y, compatibleImage.getWidth(), compatibleImage.getHeight())
                    : new Rectangle(location.x, location.y, plotRect.width, plotRect.height);
            texturePaint = new TexturePaint(compatibleImage, anchorRectangle);
            texturePaintCache.put(configuration, texturePaint);
        }
        return texturePaint;
    }

    private synchronized void flushTexturePaintCache() {
        if (texturePaintCache == null)
            return;

        Iterator<TexturePaint> iterator = texturePaintCache.values().iterator();
        while (iterator.hasNext()) {
            TexturePaint texturePaint = iterator.next();
            iterator.remove();
            texturePaint.getImage().flush();
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        loadImageFromUrl();
    }

    private static void validateImageDimensions(Image image) {
        if (image != null && (image.getWidth(null) <= 0 || image.getHeight(null) <= 0))
            throw new IllegalArgumentException("Invalid image");
    }

    private static void validateMode(int mode) {
        switch (mode) {
            case ANCHORED:
            case TILED:
            case SCALED:
                return;
            default:
                throw new IllegalArgumentException("Invalid mode: " + mode);
        }
    }
}
