package one.chartsy.charting.util.swing;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/// Stores snapshots of custom cursors that chart interactors register by name.
///
/// `ChartEditPointInteractor` and `ChartZoomInteractor` call
/// [#registerCustomCursor(String, Image, Point)] only after `Toolkit#createCustomCursor(...)`
/// succeeds. The registry copies the image into an ARGB [BufferedImage] and clones the hot spot so
/// later code does not depend on mutable caller-owned objects.
///
/// No read API is exposed from this class. The only contract it publishes inside this module is
/// that successful registrations are recorded by name, with later registrations replacing earlier
/// ones for the same name.
public final class CursorRegistry {
    
    /// Snapshot of one custom cursor registration.
    ///
    /// Both members are owned copies captured at registration time.
    private record CursorInfo(BufferedImage image, Point hotSpot) {}
    
    private static final Map<String, CursorInfo> registeredCursors = new HashMap<>();
    
    /// Copies `image` into an ARGB buffered image owned by this registry.
    ///
    /// The returned image is detached from the caller's original image implementation so later
    /// mutations or toolkit-specific image lifecycles do not affect the stored cursor snapshot.
    private static BufferedImage createBufferedImage(Image image) {
        BufferedImage bufferedImage = new BufferedImage(
                image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        Graphics g = bufferedImage.getGraphics();
        if (g == null)
            return null;
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return bufferedImage;
    }
    
    /// Registers a successfully created custom cursor under `name`.
    ///
    /// The stored snapshot owns both the copied image pixels and the cloned hot spot. Registering
    /// the same `name` again replaces the previous snapshot.
    public static void registerCustomCursor(String name, Image image, Point hotSpot) {
        CursorInfo cursorInfo = new CursorInfo(createBufferedImage(image), (Point) hotSpot.clone());
        synchronized (registeredCursors) {
            registeredCursors.put(name, cursorInfo);
        }
    }
    
    private CursorRegistry() {
    }
}
