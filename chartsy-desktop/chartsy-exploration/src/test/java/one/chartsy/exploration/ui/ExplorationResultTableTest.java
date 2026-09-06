package one.chartsy.exploration.ui;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableColumn;
import one.chartsy.Symbol;
import one.chartsy.SymbolIdentity;
import one.chartsy.kernel.ExplorationFragment;
import org.junit.jupiter.api.Test;
import org.netbeans.swing.etable.ETableColumnModel;

import static org.assertj.core.api.Assertions.assertThat;

class ExplorationResultTableTest {
    @Test
    void pending_rows_stay_out_of_sorted_view_until_the_insert_notification() throws Exception {
        onEdt(() -> {
            var table = new ExplorationResultTable();
            table.explorationFragmentCreated(row("MIKE"));
            table.setColumnSorted(0, true, 1);
            table.setRowSelectionInterval(0, 0);

            for (String name : List.of("ZULU", "BRAVO", "ALPHA")) {
                table.explorationFragmentCreated(row(name));
                assertThat(table.getModel().getRowCount()).isEqualTo(1);
                assertThat(table.getRowCount()).isEqualTo(1);
                assertThat(table.getValueAt(table.getSelectedRow(), 0).toString()).isEqualTo("MIKE");
                assertThat(table.convertRowIndexToModel(0)).isZero();
                assertThat(table.convertRowIndexToView(0)).isZero();
            }

            table.getModel().flushPendingRows();
            assertThat(table.getRowCount()).isEqualTo(4);
            assertThat(table.getSelectedRow()).isEqualTo(2);
            assertThat(table.getValueAt(table.getSelectedRow(), 0).toString()).isEqualTo("MIKE");
            var visibleNames = new ArrayList<String>();
            for (int view = 0; view < table.getRowCount(); view++) {
                int model = table.convertRowIndexToModel(view);
                assertThat(table.convertRowIndexToView(model)).isEqualTo(view);
                visibleNames.add(table.getValueAt(view, 0).toString());
            }
            assertThat(visibleNames).containsExactly("ALPHA", "BRAVO", "MIKE", "ZULU");
            return null;
        });
    }

    @Test
    void new_column_publishes_earlier_pending_rows_in_order_without_duplicate_notifications() throws Exception {
        onEdt(() -> {
            var model = new ExplorationResult();
            model.addExplorationFragment(row("ALPHA"));
            var events = new ArrayList<Notification>();
            model.addTableModelListener(event -> events.add(new Notification(
                    event.getType(), event.getFirstRow(), event.getLastRow(), EventQueue.isDispatchThread())));
            model.addExplorationFragment(row("BRAVO"));
            assertThat(model.getRowCount()).isEqualTo(1);
            model.addExplorationFragment(rowWithDetail("CHARLIE"));
            model.flushPendingRows();
            assertThat(model.getRowCount()).isEqualTo(3);
            assertThat(model.getColumnCount()).isEqualTo(2);
            assertThat(model.getValueAt(0, 0).toString()).isEqualTo("ALPHA");
            assertThat(model.getValueAt(1, 0).toString()).isEqualTo("BRAVO");
            assertThat(model.getValueAt(2, 0).toString()).isEqualTo("CHARLIE");
            assertThat(events).containsExactly(new Notification(TableModelEvent.UPDATE, -1, -1, true));
            return null;
        });
    }

    @Test
    void explorationResultsReset_preserves_columns_and_sorting_but_clears_deleted_selection() throws Exception {
        var table = onEdt(ExplorationResultTable::new);
        table.explorationFragmentCreated(row("OLD"));
        TableColumn column = onEdt(() -> {
            table.setColumnSorted(0, true, 1);
            table.setRowSelectionInterval(0, 0);
            var first = table.getColumnModel().getColumn(0);
            first.setPreferredWidth(240);
            // This row has not yet received its batched INSERT event.
            table.explorationFragmentCreated(row("PENDING"));
            table.explorationResultsReset();
            return first;
        });
        table.explorationFragmentCreated(row("ZULU"));
        table.explorationFragmentCreated(row("ALPHA"));
        table.explorationFinished();
        onEdt(() -> {
            assertThat(table.getModel().getRowCount()).isEqualTo(2);
            assertThat(table.getSelectedRow()).isEqualTo(-1);
            assertThat(table.getColumnModel().getColumn(0)).isSameAs(column);
            assertThat(column.getPreferredWidth()).isEqualTo(240);
            assertThat(table.getValueAt(0, 0).toString()).isEqualTo("ALPHA");
            assertThat(table.getValueAt(1, 0).toString()).isEqualTo("ZULU");
            return null;
        });
    }

    @Test
    void explorationResultsReset_orders_queued_rows_and_emits_valid_delete_ranges_on_edt() throws Exception {
        var table = onEdt(ExplorationResultTable::new);
        var events = new ArrayList<Notification>();
        table.explorationFragmentCreated(row("OLD"));
        onEdt(() -> {
            table.getModel().addTableModelListener(event -> events.add(new Notification(
                    event.getType(), event.getFirstRow(), event.getLastRow(), EventQueue.isDispatchThread())));
            var producer = new FutureTask<>(() -> {
                table.explorationFragmentCreated(row("PENDING"));
                table.explorationResultsReset();
                table.explorationResultsReset();
                table.explorationFragmentCreated(row("CURRENT"));
                table.explorationFinished();
                return null;
            });
            new Thread(producer, "Exploration reset producer").start();
            producer.get();
            return null;
        });
        onEdt(() -> {
            assertThat(events).containsExactly(
                    new Notification(TableModelEvent.INSERT, 1, 1, true),
                    new Notification(TableModelEvent.DELETE, 0, 1, true),
                    new Notification(TableModelEvent.INSERT, 0, 0, true));
            assertThat(table.getValueAt(0, 0).toString()).isEqualTo("CURRENT");
            return null;
        });
    }

    @Test
    void explorationFailed_flushes_partial_results_and_does_not_report_success() throws Exception {
        var table = onEdt(ExplorationResultTable::new);
        var statuses = new ArrayList<String>();
        onEdt(() -> {
            table.addPropertyChangeListener(ExplorationResultTable.STATUS_PROPERTY, event -> {
                assertThat(EventQueue.isDispatchThread()).isTrue();
                statuses.add((String) event.getNewValue());
            });
            return null;
        });
        table.explorationStatusChanged("Reading latest session…");
        table.explorationFragmentCreated(row("ALPHA"));
        table.explorationFragmentCreated(row("BRAVO"));
        table.explorationFailed(new IllegalStateException("Source data changed; run again"));
        table.explorationFinished();
        onEdt(() -> {
            assertThat(table.getRowCount()).isEqualTo(2);
            assertThat(table.getExplorationStatus()).isEqualTo("Incomplete — Source data changed; run again");
            assertThat(statuses).containsExactly("Reading latest session…", "Incomplete — Source data changed; run again");
            return null;
        });
    }

    @Test
    void explorationFinished_preserves_sorted_selection_across_appended_batches() throws Exception {
        var table = onEdt(ExplorationResultTable::new);
        table.explorationFragmentCreated(rowWithDetail("MIKE"));
        TableColumn[] columns = onEdt(() -> {
            table.setColumnSorted(0, true, 1);
            table.setRowSelectionInterval(0, 0);
            var model = (ETableColumnModel) table.getColumnModel();
            var symbol = model.getColumn(0);
            var detail = model.getColumn(1);
            symbol.setPreferredWidth(230);
            model.setColumnHidden(detail, true);
            return new TableColumn[] {symbol, detail};
        });

        for (String name : List.of("BRAVO", "ZULU", "ALPHA")) {
            table.explorationFragmentCreated(rowWithDetail(name));
            table.explorationFinished();
            onEdt(() -> {
                assertThat(table.getSelectedRow()).isGreaterThanOrEqualTo(0);
                assertThat(table.getValueAt(table.getSelectedRow(), 0).toString()).isEqualTo("MIKE");
                assertThat(table.getColumnModel().getColumn(0)).isSameAs(columns[0]);
                assertThat(columns[0].getPreferredWidth()).isEqualTo(230);
                assertThat(((ETableColumnModel) table.getColumnModel()).isColumnHidden(columns[1])).isTrue();
                return null;
            });
        }
        assertThat(onEdt(table::getRowCount)).isEqualTo(4);
    }

    @Test
    void explorationFinished_flushes_each_pending_batch_once_on_the_event_thread() throws Exception {
        var table = onEdt(ExplorationResultTable::new);
        var events = new ArrayList<Notification>();
        onEdt(() -> {
            table.getModel().addTableModelListener(event -> events.add(new Notification(
                    event.getType(), event.getFirstRow(), event.getLastRow(), EventQueue.isDispatchThread())));
            return null;
        });
        table.explorationFragmentCreated(row("ALPHA"));
        table.explorationFinished();
        onEdt(() -> {
            assertThat(events).containsExactly(new Notification(TableModelEvent.UPDATE, -1, -1, true));
            events.clear();
            table.explorationFragmentCreated(row("BRAVO"));
            table.explorationFragmentCreated(row("CHARLIE"));
            table.explorationFragmentCreated(row("DELTA"));
            return null;
        });

        table.explorationFinished();
        table.explorationFinished();
        onEdt(() -> {
            assertThat(events).containsExactly(new Notification(TableModelEvent.INSERT, 1, 3, true));
            assertThat(table.getRowCount()).isEqualTo(4);
            return null;
        });
    }

    @Test
    void addExplorationFragment_updates_the_model_on_the_event_thread() throws Exception {
        var model = new ExplorationResult();
        var worker = new FutureTask<>(() -> {
            model.addExplorationFragment(row("ALPHA"));
            return null;
        });
        onEdt(() -> {
            var thread = new Thread(worker, "Exploration result producer");
            thread.start();
            worker.get();
            // This EDT callback blocks delivery of the queued append until it returns.
            assertThat(model.getRowCount()).isZero();
            return null;
        });
        assertThat(onEdt(model::getRowCount)).isEqualTo(1);
    }

    @Test
    void addExplorationFragment_publishes_a_contiguous_batch_before_exploration_finishes() throws Exception {
        var table = onEdt(ExplorationResultTable::new);
        var inserted = new CountDownLatch(1);
        var events = new ArrayList<Notification>();
        onEdt(() -> {
            table.explorationFragmentCreated(row("ALPHA"));
            table.getModel().addTableModelListener(event -> {
                if (event.getType() == TableModelEvent.INSERT) {
                    events.add(new Notification(event.getType(), event.getFirstRow(), event.getLastRow(),
                            EventQueue.isDispatchThread()));
                    inserted.countDown();
                }
            });
            table.explorationFragmentCreated(row("BRAVO"));
            table.explorationFragmentCreated(row("CHARLIE"));
            return null;
        });
        assertThat(inserted.await(5, TimeUnit.SECONDS)).isTrue();
        onEdt(() -> {
            assertThat(events).containsExactly(new Notification(TableModelEvent.INSERT, 1, 2, true));
            return null;
        });
    }

    private static ExplorationFragment row(String name) {
        var fragment = ExplorationFragment.builder(new Symbol(SymbolIdentity.of(name), null));
        fragment.addColumn("Symbol", name);
        return fragment.build();
    }

    private static ExplorationFragment rowWithDetail(String name) {
        var fragment = ExplorationFragment.builder(new Symbol(SymbolIdentity.of(name), null));
        fragment.addColumn("Symbol", name);
        fragment.addColumn("Details", name + " details");
        return fragment.build();
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        EventQueue.invokeAndWait(task);
        return task.get();
    }

    private record Notification(int type, int firstRow, int lastRow, boolean onEventThread) { }
}
