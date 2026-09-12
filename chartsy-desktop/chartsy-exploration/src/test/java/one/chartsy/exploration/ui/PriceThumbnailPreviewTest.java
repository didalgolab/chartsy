package one.chartsy.exploration.ui;

import one.chartsy.Symbol;
import one.chartsy.SymbolIdentity;
import one.chartsy.kernel.ExplorationFragment;
import org.junit.jupiter.api.Test;
import org.netbeans.swing.etable.ETableColumnModel;

import javax.swing.JViewport;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PriceThumbnailPreviewTest {
    private static final Rectangle SCREEN = new Rectangle(0, 0, 1800, 1000);

    @Test
    void space_key_sequence_toggles_preview_before_quick_search_and_preserves_normal_typing() throws Exception {
        Fixture fixture = fixture();
        try {
            onEdt(() -> {
                fixture.table().setRowSelectionInterval(0, 0);
                fixture.show();
                fixture.table().sendKey(KeyEvent.KEY_PRESSED, KeyEvent.VK_SPACE, ' ');
                assertNull(fixture.preview().shownTarget(), "Space must toggle the active preview, before ETable's listener eats it");
                assertEquals(1, fixture.popups().hidden);
                // Repeated key-down must not toggle again while the user holds Space.
                fixture.table().sendKey(KeyEvent.KEY_PRESSED, KeyEvent.VK_SPACE, ' ');
                fixture.table().sendKey(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, ' ');
                fixture.table().sendKey(KeyEvent.KEY_RELEASED, KeyEvent.VK_SPACE, ' ');
                assertEquals(0, fixture.table().searchRequests, "The typed Space must not open ETable Quick Search");

                fixture.table().sendKey(KeyEvent.KEY_PRESSED, KeyEvent.VK_F, 'F');
                fixture.table().sendKey(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 'F');
                fixture.table().sendKey(KeyEvent.KEY_RELEASED, KeyEvent.VK_F, 'F');
                assertTrue(fixture.table().searchRequests > 0, "Ordinary text must retain ETable Quick Search");
                return null;
            });
        } finally {
            fixture.close();
        }
    }

    @Test
    void space_without_a_visible_thumbnail_keeps_etables_original_quick_search_behavior() throws Exception {
        Fixture fixture = fixture();
        try {
            onEdt(() -> {
                var columns = (ETableColumnModel) fixture.table().getColumnModel();
                columns.setColumnHidden(columns.getColumn(1), true);
                fixture.table().setRowSelectionInterval(0, 0);
                fixture.table().sendKey(KeyEvent.KEY_PRESSED, KeyEvent.VK_SPACE, ' ');
                fixture.table().sendKey(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, ' ');
                fixture.table().sendKey(KeyEvent.KEY_RELEASED, KeyEvent.VK_SPACE, ' ');
                assertTrue(fixture.table().searchRequests > 0);
                assertEquals(0, fixture.popups().shown);
                return null;
            });
        } finally {
            fixture.close();
        }
    }

    @Test
    void show_remains_visible_beyond_four_seconds_without_changing_global_tooltip_delays() throws Exception {
        Fixture fixture = fixture();
        int dismissDelay = ToolTipManager.sharedInstance().getDismissDelay();
        int initialDelay = ToolTipManager.sharedInstance().getInitialDelay();
        int reshowDelay = ToolTipManager.sharedInstance().getReshowDelay();
        var elapsed = new CountDownLatch(1);
        try {
            onEdt(() -> {
                fixture.show();
                var wait = new Timer(4_250, event -> elapsed.countDown());
                wait.setRepeats(false);
                wait.start();
                return null;
            });
            assertTrue(elapsed.await(7, TimeUnit.SECONDS));
            onEdt(() -> {
                assertEquals("FIRST", fixture.preview().shownTarget().symbol());
                assertEquals(1, fixture.popups().shown);
                assertEquals(0, fixture.popups().hidden);
                assertEquals(dismissDelay, ToolTipManager.sharedInstance().getDismissDelay());
                assertEquals(initialDelay, ToolTipManager.sharedInstance().getInitialDelay());
                assertEquals(reshowDelay, ToolTipManager.sharedInstance().getReshowDelay());
                return null;
            });
        } finally {
            fixture.close();
        }
    }

    @Test
    void pointer_can_move_from_the_cell_into_preview_and_leaving_both_removes_global_listener() throws Exception {
        Fixture fixture = fixture();
        try {
            onEdt(() -> {
                int listeners = Toolkit.getDefaultToolkit().getAWTEventListeners().length;
                fixture.show();
                assertEquals(listeners + 1, Toolkit.getDefaultToolkit().getAWTEventListeners().length);
                Rectangle bounds = fixture.popups().bounds;
                fixture.preview().movedOnScreen(new Point(bounds.x + 8, bounds.y + 8));
                assertNotNull(fixture.preview().shownTarget());
                fixture.preview().movedOnScreen(new Point(1700, 900));
                assertNull(fixture.preview().shownTarget());
                assertEquals(1, fixture.popups().hidden);
                assertEquals(listeners, Toolkit.getDefaultToolkit().getAWTEventListeners().length);
                return null;
            });
        } finally {
            fixture.close();
        }
    }

    @Test
    void ordinary_insert_keeps_preview_when_symbol_and_anchor_stay_unchanged() throws Exception {
        Fixture fixture = fixture();
        try {
            onEdt(() -> {
                fixture.show();
                fixture.table().explorationFragmentCreated(row("LAST", 10, 20));
                fixture.table().getModel().flushPendingRows();
                // A taller table after another online batch must not be mistaken for a scroll.
                fixture.table().setSize(600, 260);
                return null;
            });
            onEdt(() -> {
                assertEquals("FIRST", fixture.preview().shownTarget().symbol());
                assertEquals(0, fixture.popups().hidden);
                return null;
            });
        } finally {
            fixture.close();
        }
    }

    @Test
    void sorted_insert_closes_preview_when_its_symbol_moves_away_from_the_anchor() throws Exception {
        Fixture fixture = fixture();
        try {
            onEdt(() -> {
                fixture.table().setColumnSorted(1, true, 1);
                fixture.show();
                fixture.table().explorationFragmentCreated(row("LOWER", 10, 5));
                fixture.table().getModel().flushPendingRows();
                return null;
            });
            onEdt(() -> {
                assertNull(fixture.preview().shownTarget());
                assertEquals(1, fixture.popups().hidden);
                var cell = fixture.table().getCellRect(0, 1, true);
                var target = fixture.table().thumbnailTargetAt(new Point(cell.x + 4, cell.y + 4));
                assertEquals("LOWER", target.symbol());
                return null;
            });
        } finally {
            fixture.close();
        }
    }

    @Test
    void escape_reset_column_hide_and_detach_each_dismiss_the_owned_popup() throws Exception {
        Fixture fixture = fixture();
        try {
            onEdt(() -> {
                fixture.show();
                fixture.table().getActionMap().get("dismiss-price-preview").actionPerformed(
                        new ActionEvent(fixture.table(), ActionEvent.ACTION_PERFORMED, "Escape"));
                assertNull(fixture.preview().shownTarget());
                fixture.show();
                var columns = (ETableColumnModel) fixture.table().getColumnModel();
                var chart = columns.getColumn(1);
                columns.setColumnHidden(chart, true);
                assertNull(fixture.preview().shownTarget());
                columns.setColumnHidden(chart, false);
                fixture.show();
                fixture.table().explorationResultsReset();
                assertNull(fixture.preview().shownTarget());
                fixture.table().explorationFragmentCreated(row("FIRST", 10, 12));
                fixture.table().explorationFinished();
                fixture.show();
                fixture.preview().detach();
                assertNull(fixture.preview().shownTarget());
                assertEquals(4, fixture.popups().hidden);
                return null;
            });
        } finally {
            fixture.close();
        }
    }

    @Test
    void viewport_growth_keeps_preview_but_scroll_dismisses_and_detach_removes_viewport_listener() throws Exception {
        Fixture fixture = fixture();
        try {
            JViewport viewport = onEdt(() -> {
                var view = new JViewport();
                view.setView(fixture.table());
                view.setExtentSize(new Dimension(600, 200));
                view.setViewSize(new Dimension(600, 400));
                fixture.preview().attach();
                return view;
            });
            onEdt(() -> {
                fixture.show();
                int listeners = viewport.getChangeListeners().length;
                viewport.setViewSize(new Dimension(600, 450));
                assertNotNull(fixture.preview().shownTarget());
                viewport.setViewPosition(new Point(0, 10));
                assertNull(fixture.preview().shownTarget());
                fixture.preview().detach();
                assertEquals(listeners - 1, viewport.getChangeListeners().length);
                return null;
            });
        } finally {
            fixture.close();
        }
    }

    @Test
    void outside_click_dismisses_but_clicking_inside_preview_does_not() throws Exception {
        Fixture fixture = fixture();
        try {
            onEdt(() -> {
                fixture.show();
                Rectangle bounds = fixture.popups().bounds;
                dispatchPress(fixture, new Point(bounds.x + 8, bounds.y + 8));
                assertNotNull(fixture.preview().shownTarget());
                dispatchPress(fixture, new Point(1700, 900));
                assertNull(fixture.preview().shownTarget());
                return null;
            });
        } finally {
            fixture.close();
        }
    }

    @Test
    void locationBeside_keeps_preview_on_screen_and_never_covers_its_cell() {
        var size = new Dimension(360, 220);
        for (Rectangle screen : new Rectangle[] {SCREEN, new Rectangle(-1600, 0, 1600, 900)}) {
            for (Rectangle anchor : new Rectangle[] {
                    new Rectangle(screen.x + 50, 10, 150, 30),
                    new Rectangle(screen.x + screen.width - 160, 300, 150, 30),
                    new Rectangle(screen.x + 500, screen.height - 30, 150, 30),
                    new Rectangle(screen.x + 100, 350, screen.width - 200, 30)}) {
                Point location = PriceThumbnailPreview.locationBeside(anchor, size, screen);
                assertNotNull(location);
                var popup = new Rectangle(location, size);
                assertTrue(screen.contains(popup));
                assertFalse(popup.intersects(anchor));
                assertTrue(popup.x == anchor.x + anchor.width || popup.x + popup.width == anchor.x
                        || popup.y == anchor.y + anchor.height || popup.y + popup.height == anchor.y,
                        "The preview must touch the cell, so entering it needs no expiration/grace timer");
            }
        }
    }

    private static void dispatchPress(Fixture fixture, Point screen) {
        var event = new MouseEvent(fixture.popups().content, MouseEvent.MOUSE_PRESSED, 0, 0,
                1, 1, screen.x, screen.y, 1, false, MouseEvent.BUTTON1);
        fixture.popups().content.dispatchEvent(event);
    }

    private static Fixture fixture() throws Exception {
        return onEdt(() -> {
            var factory = new RecordingPopupFactory();
            var table = new PreviewTable(factory);
            table.explorationFragmentCreated(row("FIRST", 10, 12));
            table.setSize(600, 200);
            table.doLayout();
            return new Fixture(table, factory);
        });
    }

    private record Fixture(PreviewTable table, RecordingPopupFactory popups) {
        PriceThumbnailPreview preview() { return table.thumbnailPreview(); }
        void show() {
            Rectangle cell = table.getCellRect(0, 1, true);
            var target = table.thumbnailTargetAt(new Point(cell.x + 4, cell.y + 4));
            cell.translate(100, 100);
            preview().show(target, cell, SCREEN, false);
        }
        void close() throws Exception {
            onEdt(() -> {
                preview().detach();
                return null;
            });
        }
    }

    private static final class PreviewTable extends ExplorationResultTable {
        int searchRequests;
        PreviewTable(PopupFactory factory) { super(new ExplorationResult(), factory); }
        @Override public boolean isShowing() { return true; }
        @Override public Point getLocationOnScreen() { return new Point(100, 100); }
        @Override public void displaySearchField() { searchRequests++; }
        void sendKey(int id, int code, char character) {
            processKeyEvent(new KeyEvent(this, id, 0, 0, code, character));
        }
    }

    private static final class RecordingPopupFactory extends PopupFactory {
        int shown;
        int hidden;
        Rectangle bounds;
        Component content;
        @Override public Popup getPopup(Component owner, Component contents, int x, int y) {
            content = contents;
            bounds = new Rectangle(new Point(x, y), contents.getPreferredSize());
            return new Popup() {
                @Override public void show() { shown++; }
                @Override public void hide() { hidden++; }
            };
        }
    }

    private static ExplorationFragment row(String symbol, double first, double last) {
        var fragment = ExplorationFragment.builder(new Symbol(SymbolIdentity.of(symbol), null));
        fragment.addColumn("Symbol", symbol);
        fragment.addColumn("10M chart", PriceThumbnailRendererTest.prices(first, last));
        return fragment.build();
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        EventQueue.invokeAndWait(task);
        return task.get();
    }
}
