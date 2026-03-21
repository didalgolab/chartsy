package one.chartsy.charting.graphic;

import java.awt.Graphics;
import java.awt.Point;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.net.URL;
import java.util.Objects;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SwingConstants;

import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.util.GraphicUtil;

/// Paints an icon annotation for one renderer-visible [DisplayPoint].
///
/// The annotation keeps a fixed [Icon], a placement constant interpreted with the compass-style
/// [SwingConstants] positions, and a raw pixel offset measured away from the anchor point.
/// [#draw(Graphics, DisplayPoint)] and [#getBounds(DisplayPoint, Rectangle2D)] share the same
/// placement logic, so repaint and plot-layout code observe the same geometry that is actually
/// painted.
///
/// The icon is retained by reference and painted with the owning chart as the Swing paint context.
/// Public `set...` methods do not mutate this instance; they return a new annotation that reuses
/// the current settings.
///
/// ### API Notes
///
/// - [#NORTH], [#NORTH_EAST], [#EAST], [#SOUTH_EAST], [#SOUTH], [#SOUTH_WEST], [#WEST], and
///   [#NORTH_WEST] place the icon around the anchor point with `offset` pixels of gap.
/// - Any other position value, including [#CENTER], falls back to centered placement and ignores
///   the offset.
/// - An annotation built from a [URL] retains that source for [#getURL()]. Copy-style updates from
///   [#setOffset(int)] and [#setPosition(int)] preserve the retained URL, while
///   [#setIcon(Icon)] clears it.
public class DefaultDataAnnotation implements DataAnnotation, SwingConstants, Serializable {
    private final Icon icon;
    private final int position;
    private final int offset;
    private final URL iconUrl;

    /// Computes the icon's upper-left paint location from an anchor point.
    ///
    /// The supplied `anchor` point is translated in place and returned.
    ///
    /// @param icon     icon to place
    /// @param position compass position relative to `anchor`
    /// @param offset   raw pixel gap applied away from `anchor`
    /// @param anchor   display-space anchor point to translate
    /// @return `anchor`, translated to the icon's paint origin
    public static Point computeLocation(Icon icon, int position, int offset, Point anchor) {
        int iconWidth = icon.getIconWidth();
        int iconHeight = icon.getIconHeight();

        switch (position) {
            case NORTH -> anchor.translate(-iconWidth / 2, -(iconHeight + offset));
            case NORTH_EAST -> anchor.translate(offset, -(iconHeight + offset));
            case EAST -> anchor.translate(offset, -iconHeight / 2);
            case SOUTH_EAST -> anchor.translate(offset, offset);
            case SOUTH -> anchor.translate(-iconWidth / 2, offset);
            case SOUTH_WEST -> anchor.translate(-(iconWidth + offset), offset);
            case WEST -> anchor.translate(-(iconWidth + offset), -iconHeight / 2);
            case NORTH_WEST -> anchor.translate(-(iconWidth + offset), -(iconHeight + offset));
            default -> anchor.translate(-(int) (iconWidth / 2.0), -(int) (iconHeight / 2.0));
        }
        return anchor;
    }

    /// Creates a centered annotation with no gap around its anchor point.
    ///
    /// @param icon icon to paint
    public DefaultDataAnnotation(Icon icon) {
        this(icon, null, CENTER, 0);
    }

    /// Creates an annotation that paints `icon` relative to each anchor point.
    ///
    /// `position` is interpreted by [#computeLocation(Icon, int, int, Point)].
    ///
    /// @param icon     icon to paint
    /// @param position placement constant relative to the anchor point
    /// @param offset   raw pixel gap applied away from the anchor point
    public DefaultDataAnnotation(Icon icon, int position, int offset) {
        this(icon, null, position, offset);
    }

    /// Creates a centered annotation from an icon resource URL.
    ///
    /// This constructor eagerly loads the current [ImageIcon] from `iconUrl` and retains the URL
    /// for later retrieval through [#getURL()].
    ///
    /// @param iconUrl URL used to create the icon
    public DefaultDataAnnotation(URL iconUrl) {
        this(iconUrl, CENTER, 0);
    }

    /// Creates an annotation from an icon resource URL.
    ///
    /// This constructor eagerly loads the current [ImageIcon] from `iconUrl` and retains the URL
    /// for later retrieval through [#getURL()].
    ///
    /// @param iconUrl  URL used to create the icon
    /// @param position placement constant relative to the anchor point
    /// @param offset   raw pixel gap applied away from the anchor point
    public DefaultDataAnnotation(URL iconUrl, int position, int offset) {
        this(new ImageIcon(iconUrl), iconUrl, position, offset);
    }

    private DefaultDataAnnotation(Icon icon, URL iconUrl, int position, int offset) {
        this.icon = Objects.requireNonNull(icon, "Icon cannot be null");
        this.position = position;
        this.offset = offset;
        this.iconUrl = iconUrl;
    }

    /// Computes the icon's paint origin for `displayPoint`.
    ///
    /// The display coordinates are rounded with [GraphicUtil#toInt(double)] before placement so
    /// bounds and painting use the same pixel anchor.
    ///
    /// @param displayPoint renderer-visible point being annotated
    /// @return icon paint origin in display coordinates
    protected Point computeIconLocation(DisplayPoint displayPoint) {
        Point anchor = new Point(
                GraphicUtil.toInt(displayPoint.getXCoord()),
                GraphicUtil.toInt(displayPoint.getYCoord()));
        return computeLocation(icon, position, offset, anchor);
    }

    /// Paints the icon annotation for `displayPoint`.
    ///
    /// The owning chart component is passed to [Icon#paintIcon] as the Swing paint context.
    @Override
    public void draw(Graphics g, DisplayPoint displayPoint) {
        Point iconLocation = computeIconLocation(displayPoint);
        icon.paintIcon(displayPoint.getRenderer().getChart(), g, iconLocation.x, iconLocation.y);
    }

    /// Returns the bounds of the icon that [#draw(Graphics, DisplayPoint)] would paint.
    ///
    /// A non-`null` `bounds` rectangle is reused in place.
    @Override
    public Rectangle2D getBounds(DisplayPoint displayPoint, Rectangle2D bounds) {
        Point iconLocation = computeIconLocation(displayPoint);
        if (bounds != null) {
            bounds.setRect(iconLocation.x, iconLocation.y, icon.getIconWidth(), icon.getIconHeight());
            return bounds;
        }
        return new Rectangle2D.Double(
                iconLocation.x,
                iconLocation.y,
                icon.getIconWidth(),
                icon.getIconHeight());
    }

    /// Returns the retained icon reference.
    ///
    /// @return icon painted by this annotation
    public final Icon getIcon() {
        return icon;
    }

    /// Returns the raw pixel gap applied between the anchor point and the icon.
    ///
    /// @return placement gap in pixels
    public final int getOffset() {
        return offset;
    }

    /// Returns the placement constant interpreted by [#computeLocation(Icon, int, int, Point)].
    ///
    /// @return placement constant relative to the anchor point
    public final int getPosition() {
        return position;
    }

    /// Returns the original URL used to create the icon, if one is retained.
    ///
    /// @return source URL for the retained icon, or `null`
    public final URL getURL() {
        return iconUrl;
    }

    /// Returns a new annotation that paints `icon` with the current placement settings.
    ///
    /// The returned annotation does not retain a source URL.
    ///
    /// @param icon replacement icon to paint
    /// @return copied annotation with `icon`
    public final DefaultDataAnnotation setIcon(Icon icon) {
        return new DefaultDataAnnotation(icon, position, offset);
    }

    /// Returns a new annotation with the same icon and position but a different gap.
    ///
    /// @param offset replacement pixel gap
    /// @return copied annotation with `offset`
    public final DefaultDataAnnotation setOffset(int offset) {
        return new DefaultDataAnnotation(icon, iconUrl, position, offset);
    }

    /// Returns a new annotation with the same icon and offset but a different placement constant.
    ///
    /// @param position replacement placement constant
    /// @return copied annotation with `position`
    public final DefaultDataAnnotation setPosition(int position) {
        return new DefaultDataAnnotation(icon, iconUrl, position, offset);
    }
}
