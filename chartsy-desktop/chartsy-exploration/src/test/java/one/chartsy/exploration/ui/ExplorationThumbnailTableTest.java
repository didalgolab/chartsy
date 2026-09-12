package one.chartsy.exploration.ui;

import one.chartsy.Symbol;
import one.chartsy.SymbolIdentity;
import one.chartsy.kernel.ExplorationFragment;
import org.junit.jupiter.api.Test;
import org.netbeans.swing.etable.ETableColumnModel;

import javax.swing.event.TableModelEvent;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import static org.junit.jupiter.api.Assertions.*;

class ExplorationThumbnailTableTest {
    @Test
    void thumbnail_column_has_readable_dimensions_and_hiding_restores_compact_rows() throws Exception {
        onEdt(() -> {
            var table = new ExplorationResultTable();
            int compact = table.getRowHeight();
            table.explorationFragmentCreated(row("ONE", 10, 15));
            var columns = (ETableColumnModel) table.getColumnModel();
            var chart = columns.getColumn(1);
            assertInstanceOf(PriceThumbnailRenderer.class, chart.getCellRenderer());
            assertTrue(chart.getPreferredWidth() >= 140);
            assertTrue(chart.getMinWidth() >= 90);
            assertTrue(table.getRowHeight() >= 28 && table.getRowHeight() <= 34);
            chart.setPreferredWidth(231);
            columns.setColumnHidden(chart, true);
            assertEquals(compact, table.getRowHeight());
            columns.setColumnHidden(chart, false);
            assertTrue(table.getRowHeight() >= 28 && table.getRowHeight() <= 34);
            assertEquals(231, chart.getPreferredWidth());
            return null;
        });
    }

    @Test
    void thumbnail_sorting_preserves_selected_symbol_and_custom_column_through_online_batches() throws Exception {
        onEdt(() -> {
            var table = new ExplorationResultTable();
            table.explorationFragmentCreated(row("MID", 100, 110));
            table.setColumnSorted(1, true, 1);
            table.setRowSelectionInterval(0, 0);
            var chartColumn = table.getColumnModel().getColumn(1);
            chartColumn.setPreferredWidth(213);
            var events = new ArrayList<Integer>();
            table.getModel().addTableModelListener(event -> {
                assertTrue(EventQueue.isDispatchThread());
                events.add(event.getType());
            });
            table.explorationFragmentCreated(row("HIGH", 10, 20));
            table.explorationFragmentCreated(row("LOW", 100, 90));
            assertEquals(1, table.getRowCount(), "Pending arrivals stay unpublished until their insert event");
            table.getModel().flushPendingRows();
            assertEquals(List.of(TableModelEvent.INSERT), events);
            assertEquals(List.of("LOW", "MID", "HIGH"), visibleSymbols(table));
            assertEquals("MID", table.getValueAt(table.getSelectedRow(), 0).toString());
            table.explorationFragmentCreated(row("BETWEEN", 100, 105));
            table.getModel().flushPendingRows();
            assertEquals(List.of("LOW", "BETWEEN", "MID", "HIGH"), visibleSymbols(table));
            assertEquals("MID", table.getValueAt(table.getSelectedRow(), 0).toString());
            assertSame(chartColumn, table.getColumnModel().getColumn(1));
            assertEquals(213, chartColumn.getPreferredWidth());
            for (int view = 0; view < table.getRowCount(); view++)
                assertEquals(view, table.convertRowIndexToView(table.convertRowIndexToModel(view)));
            return null;
        });
    }

    @Test
    void preview_target_uses_sorted_rows_and_reordered_columns_without_suppressing_other_tooltips() throws Exception {
        onEdt(() -> {
            var table = new ExplorationResultTable();
            table.explorationFragmentCreated(row("LOW", 100, 90));
            table.explorationFragmentCreated(row("HIGH", 10, 20));
            table.explorationFinished();
            table.setColumnSorted(1, false, 1);
            table.moveColumn(1, 0);
            table.setSize(420, 200);
            table.doLayout();
            var cell = table.getCellRect(0, 0, false);
            var chartPoint = new Point(cell.x + cell.width / 2, cell.y + cell.height / 2);
            var target = table.thumbnailTargetAt(chartPoint);
            assertNotNull(target);
            assertEquals("HIGH", target.symbol());
            assertEquals(100, target.prices().changePercent());
            assertEquals(1, target.modelRow());
            assertEquals(1, target.modelColumn());
            table.setToolTipText("Ordinary table tooltip");
            assertNull(table.getToolTipText(new MouseEvent(table, MouseEvent.MOUSE_MOVED,
                    0, 0, chartPoint.x, chartPoint.y, 0, false)), "Only chart cells use the persistent popup");
            var textCell = table.getCellRect(0, 1, false);
            assertEquals("Ordinary table tooltip", table.getToolTipText(new MouseEvent(table, MouseEvent.MOUSE_MOVED,
                    0, 0, textCell.x + textCell.width / 2, textCell.y + textCell.height / 2, 0, false)));
            return null;
        });
    }

    @Test
    void hidden_thumbnail_keeps_user_settings_across_reset_and_new_rows() throws Exception {
        onEdt(() -> {
            var table = new ExplorationResultTable();
            table.explorationFragmentCreated(row("OLD", 10, 20));
            var columns = (ETableColumnModel) table.getColumnModel();
            var chart = columns.getColumn(1);
            chart.setPreferredWidth(240);
            columns.setColumnHidden(chart, true);
            int compact = table.getRowHeight();
            table.explorationResultsReset();
            table.explorationFragmentCreated(row("NEW", 10, 12));
            table.explorationFinished();
            assertTrue(columns.isColumnHidden(chart));
            assertEquals(240, chart.getPreferredWidth());
            assertEquals(compact, table.getRowHeight());
            assertEquals(1, table.getColumnCount());
            assertEquals("NEW", table.getValueAt(0, 0).toString());
            return null;
        });
    }

    private static List<String> visibleSymbols(ExplorationResultTable table) {
        var symbols = new ArrayList<String>();
        for (int row = 0; row < table.getRowCount(); row++)
            symbols.add(table.getValueAt(row, 0).toString());
        return symbols;
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
