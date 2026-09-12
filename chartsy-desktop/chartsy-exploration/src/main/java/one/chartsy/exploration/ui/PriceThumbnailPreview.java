package one.chartsy.exploration.ui;

import one.chartsy.misc.PriceSeriesThumbnail;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JToolTip;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import java.awt.AWTEvent;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.util.Objects;

/** A cell preview lasts until the user leaves or dismisses it, never until a timeout. */
final class PriceThumbnailPreview {
    record Target(PriceSeriesThumbnail prices, String symbol, int modelRow, int modelColumn) { }

    private final ExplorationResultTable table;
    private final PopupFactory popupFactory;
    private final Timer intent;
    private final AWTEventListener outsideEvents = this::outsideEvent;
    private final ChangeListener scrolled = event -> viewportChanged();
    private JViewport viewport;
    private Point viewportPosition;
    private Dimension viewportExtent;
    private Target pending;
    private Target shown;
    private Point pointerInTable;
    private Popup popup;
    private Rectangle anchorBounds;
    private Rectangle previewBounds;
    private boolean keyboardPreview;

    PriceThumbnailPreview(ExplorationResultTable table, PopupFactory popupFactory) {
        this.table = table;
        this.popupFactory = popupFactory;
        intent = new Timer(250, event -> showPending());
        intent.setRepeats(false);
        var mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent event) { movedInTable(event.getPoint()); }
            @Override public void mouseExited(MouseEvent event) {
                if (popup == null || !table.isShowing())
                    dismiss();
                else
                    movedOnScreen(event.getLocationOnScreen());
            }
            @Override public void mousePressed(MouseEvent event) { dismiss(); }
        };
        table.addMouseListener(mouse);
        table.addMouseMotionListener(mouse);
        table.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !table.isShowing())
                dismiss();
            if ((event.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0)
                attach();
        });
        table.addComponentListener(new ComponentAdapter() {
            @Override public void componentHidden(ComponentEvent event) { dismiss(); }
            @Override public void componentMoved(ComponentEvent event) { dismiss(); }
            @Override public void componentResized(ComponentEvent event) { validateAnchor(); }
        });
        table.getModel().addTableModelListener(event -> {
            if (event.getFirstRow() == TableModelEvent.HEADER_ROW || event.getType() == TableModelEvent.DELETE)
                dismiss();
            else if (shown != null)
                EventQueue.invokeLater(this::validateAnchor);
        });
        table.getSelectionModel().addListSelectionListener(event -> {
            if (keyboardPreview && !event.getValueIsAdjusting())
                EventQueue.invokeLater(() -> {
                    if (keyboardPreview && !Objects.equals(shown, table.selectedThumbnailTarget()))
                        dismiss();
                });
        });
        bind(KeyEvent.VK_SPACE, "toggle-price-preview", true);
        bind(KeyEvent.VK_ESCAPE, "dismiss-price-preview", false);
    }

    private void bind(int keyCode, String actionName, boolean toggle) {
        var input = table.getInputMap(JComponent.WHEN_FOCUSED);
        KeyStroke key = KeyStroke.getKeyStroke(keyCode, 0);
        Action original = table.getActionMap().get(input.get(key));
        input.put(key, actionName);
        table.getActionMap().put(actionName, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                boolean handled = toggle ? toggleSelected() : dismissIfPresent();
                if (!handled && original != null && original.isEnabled())
                    original.actionPerformed(event);
            }
        });
    }

    void attach() {
        JViewport current = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, table);
        if (viewport != current) {
            if (viewport != null)
                viewport.removeChangeListener(scrolled);
            viewport = current;
            if (viewport != null) {
                viewportPosition = viewport.getViewPosition();
                viewportExtent = viewport.getExtentSize();
                viewport.addChangeListener(scrolled);
            }
        }
    }

    private void viewportChanged() {
        Point position = viewport.getViewPosition();
        Dimension extent = viewport.getExtentSize();
        if (!position.equals(viewportPosition) || !extent.equals(viewportExtent))
            dismiss();
        viewportPosition = position;
        viewportExtent = extent;
    }

    void detach() {
        dismiss();
        if (viewport != null) {
            viewport.removeChangeListener(scrolled);
            viewport = null;
        }
    }

    void movedInTable(Point point) {
        pointerInTable = new Point(point);
        Target target = table.thumbnailTargetAt(point);
        if (Objects.equals(target, shown) && shown != null)
            return;
        if (pending != null && pending.equals(target))
            return;
        dismiss();
        if (target != null) {
            pending = target;
            intent.restart();
        }
    }

    private void showPending() {
        Target target = pending;
        if (target == null || !table.isShowing())
            return;
        if (!target.equals(table.thumbnailTargetAt(pointerInTable))) {
            movedInTable(pointerInTable);
            return;
        }
        show(target, false);
    }

    boolean toggleSelected() {
        Target target = table.selectedThumbnailTarget();
        if (target == null)
            return false;
        if (target.equals(shown))
            dismiss();
        else
            show(target, true);
        return true;
    }

    private void show(Target target, boolean keyboard) {
        Rectangle anchor = targetBounds(target);
        GraphicsConfiguration configuration = table.getGraphicsConfiguration();
        if (anchor == null || configuration == null) {
            dismiss();
            return;
        }
        Rectangle available = new Rectangle(configuration.getBounds());
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        available.x += insets.left;
        available.y += insets.top;
        available.width -= insets.left + insets.right;
        available.height -= insets.top + insets.bottom;
        show(target, anchor, available, keyboard);
    }

    // Screen geometry and PopupFactory are explicit here so lifecycle/placement can be tested without a desktop.
    void show(Target target, Rectangle anchor, Rectangle available, boolean keyboard) {
        dismiss();
        JToolTip content = PriceThumbnailRenderer.preview(target.prices(), target.symbol());
        content.setComponent(table);
        content.setTipText(target.symbol() + " · " + PriceThumbnailRenderer.previewSummary(target.prices()));
        Point location = locationBeside(anchor, content.getPreferredSize(), available);
        if (location == null)
            return;
        popup = popupFactory.getPopup(table, content, location.x, location.y);
        shown = target;
        keyboardPreview = keyboard;
        anchorBounds = new Rectangle(anchor);
        previewBounds = new Rectangle(location, content.getPreferredSize());
        Toolkit.getDefaultToolkit().addAWTEventListener(outsideEvents,
                AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK
                        | AWTEvent.MOUSE_WHEEL_EVENT_MASK | AWTEvent.KEY_EVENT_MASK | AWTEvent.WINDOW_EVENT_MASK);
        try {
            popup.show();
        } catch (RuntimeException failure) {
            dismiss();
            throw failure;
        }
    }

    static Point locationBeside(Rectangle anchor, Dimension size, Rectangle screen) {
        int x = Math.max(screen.x, Math.min(anchor.x, screen.x + screen.width - size.width));
        int y = Math.max(screen.y, Math.min(anchor.y, screen.y + screen.height - size.height));
        Point[] candidates = {new Point(anchor.x + anchor.width, y), new Point(anchor.x - size.width, y),
                new Point(x, anchor.y + anchor.height), new Point(x, anchor.y - size.height)};
        for (Point candidate : candidates) {
            var bounds = new Rectangle(candidate, size);
            if (screen.contains(bounds) && !bounds.intersects(anchor))
                return candidate;
        }
        return null;
    }

    void movedOnScreen(Point point) {
        if (popup == null)
            return;
        if (previewBounds.contains(point))
            return;
        if (anchorBounds.contains(point)) {
            validateAnchor();
            return;
        }
        dismiss();
        if (table.isShowing()) {
            Point local = new Point(point);
            SwingUtilities.convertPointFromScreen(local, table);
            if (table.contains(local))
                movedInTable(local);
        }
    }

    void validateAnchor() {
        if (shown != null && !Objects.equals(anchorBounds, targetBounds(shown)))
            dismiss();
    }

    private Rectangle targetBounds(Target target) {
        if (!table.isShowing() || target.modelRow() >= table.getModel().getRowCount()
                || target.modelColumn() >= table.getModel().getColumnCount()
                || PriceThumbnailRenderer.thumbnail(table.getModel().getValueAt(target.modelRow(), target.modelColumn())) != target.prices())
            return null;
        int row = table.convertRowIndexToView(target.modelRow());
        int column = table.convertColumnIndexToView(target.modelColumn());
        if (row < 0 || column < 0)
            return null;
        Rectangle bounds = table.getCellRect(row, column, true).intersection(table.getVisibleRect());
        if (bounds.isEmpty())
            return null;
        Point origin = table.getLocationOnScreen();
        bounds.translate(origin.x, origin.y);
        return bounds;
    }

    private void outsideEvent(AWTEvent event) {
        if (event instanceof MouseEvent mouse) {
            if (mouse.getID() == MouseEvent.MOUSE_WHEEL)
                dismiss();
            else if (mouse.getID() == MouseEvent.MOUSE_PRESSED) {
                if (previewBounds != null && !previewBounds.contains(mouse.getLocationOnScreen()))
                    dismiss();
            } else if (mouse.getID() == MouseEvent.MOUSE_MOVED || mouse.getID() == MouseEvent.MOUSE_DRAGGED
                    || mouse.getID() == MouseEvent.MOUSE_EXITED)
                movedOnScreen(mouse.getLocationOnScreen());
        } else if (event instanceof KeyEvent key && key.getID() == KeyEvent.KEY_PRESSED && key.getKeyCode() == KeyEvent.VK_ESCAPE) {
            if (dismissIfPresent())
                key.consume();
        } else if (event instanceof WindowEvent window && window.getID() == WindowEvent.WINDOW_DEACTIVATED
                && window.getWindow() == SwingUtilities.getWindowAncestor(table))
            dismiss();
    }

    private boolean dismissIfPresent() {
        boolean present = shown != null || pending != null;
        dismiss();
        return present;
    }

    void dismiss() {
        intent.stop();
        pending = null;
        shown = null;
        keyboardPreview = false;
        anchorBounds = null;
        previewBounds = null;
        if (popup != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(outsideEvents);
            Popup previous = popup;
            popup = null;
            previous.hide();
        }
    }

    Target shownTarget() {
        return shown;
    }
}
