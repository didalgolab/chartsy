package one.chartsy.charting.util.java2d;

import java.awt.TexturePaint;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.Objects;

import javax.imageio.ImageIO;

/// [TexturePaint] that retains the source image URL used to build the tile.
///
/// Charting code serializes and reconstructs textures through that URL together with the anchor
/// rectangle. Equality therefore compares both the anchor geometry and the originating image URL,
/// not just the inherited `TexturePaint` instance identity.
///
/// The image is loaded eagerly in the constructor so a deserialized or reconstructed texture is
/// immediately ready for painting. The retained URL is exposed again through [#getImageURL()] so
/// neighboring serialization code can persist the texture definition without inspecting the
/// underlying tile image.
public class Texture extends TexturePaint {
    private final URL imageUrl;

    private Texture(URL imageUrl, BufferedImage image) {
        this(imageUrl, image, new Rectangle2D.Float(0.0f, 0.0f, image.getWidth(), image.getHeight()));
    }

    private Texture(URL imageUrl, BufferedImage image, Rectangle2D anchor) {
        super(image, anchor);
        this.imageUrl = imageUrl;
    }

    private static BufferedImage readImage(URL imageUrl) throws IOException {
        return ImageIO.read(imageUrl);
    }

    /// Creates a texture whose anchor rectangle starts at `(0, 0)` and matches the decoded image
    /// size.
    ///
    /// This is the form used when `PlotStyle` reconstructs a texture from only its source URL.
    ///
    /// @param imageUrl URL used both to load the tile image and to identify this texture later
    /// @throws IOException if the image cannot be read from `imageUrl`
    public Texture(URL imageUrl) throws IOException {
        this(imageUrl, readImage(imageUrl));
    }

    /// Creates a texture with an explicit anchor rectangle.
    ///
    /// The anchor geometry participates in [#equals(Object)] and [#hashCode()] because callers use
    /// it as part of the persisted paint definition.
    ///
    /// @param imageUrl URL used both to load the tile image and to identify this texture later
    /// @param anchor   anchor rectangle, in user space, that defines one repeated tile
    /// @throws IOException if the image cannot be read from `imageUrl`
    public Texture(URL imageUrl, Rectangle2D anchor) throws IOException {
        this(imageUrl, readImage(imageUrl), anchor);
    }

    /// Compares textures by source image URL and anchor rectangle.
    ///
    /// Two textures created from the same URL and anchor geometry compare equal even though
    /// `TexturePaint` itself does not define value semantics for the decoded image instance.
    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof Texture other))
            return false;
        return getAnchorRect().equals(other.getAnchorRect())
                && Objects.equals(imageUrl, other.imageUrl);
    }

    /// Returns a hash code consistent with [#equals(Object)].
    @Override
    public int hashCode() {
        return Objects.hash(getAnchorRect(), imageUrl);
    }

    /// Returns the image URL used to build the texture tile.
    public final URL getImageURL() {
        return imageUrl;
    }
}
