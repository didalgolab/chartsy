package one.chartsy.exploration.ui;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javax.swing.event.TableModelEvent;
import one.chartsy.Symbol;
import one.chartsy.SymbolIdentity;
import one.chartsy.kernel.ExplorationFragment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExplorationResultTableTest {
    @Test
    void explorationFinished_preserves_sorted_selection_across_appended_batches() throws Exception {
        var table = onEdt(ExplorationResultTable::new);
        table.explorationFragmentCreated(row("ALPHA"));
        onEdt(() -> {
            table.setColumnSorted(0, true, 1);
            table.setRowSelectionInterval(0, 0);
            return null;
        });

        for (String name : List.of("BRAVO", "CHARLIE")) {
            table.explorationFragmentCreated(row(name));
            table.explorationFinished();
            onEdt(() -> {
                assertThat(table.getSelectedRow()).isZero();
                assertThat(table.getValueAt(table.getSelectedRow(), 0).toString()).isEqualTo("ALPHA");
                return null;
            });
        }
        assertThat(onEdt(table::getRowCount)).isEqualTo(3);
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

    private static <T> T onEdt(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        EventQueue.invokeAndWait(task);
        return task.get();
    }

    private record Notification(int type, int firstRow, int lastRow, boolean onEventThread) { }
}
