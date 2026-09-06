/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.exploration;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import one.chartsy.Candle;
import one.chartsy.Symbol;
import one.chartsy.SymbolGroup;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DataQuery;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.data.provider.LatestCandleTimeProvider;
import one.chartsy.exploration.ui.ExplorationConfiguration;
import one.chartsy.exploration.ui.ImmutableExplorationConfiguration;
import one.chartsy.kernel.Exploration;
import one.chartsy.kernel.ExplorationFragment;
import one.chartsy.kernel.ExplorationListener;
import one.chartsy.kernel.ProgressHandle;
import one.chartsy.kernel.runner.ImmutableLaunchContext;
import one.chartsy.time.Chronological;
import org.junit.jupiter.api.Test;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.*;

class ExplorationRunnerTest {
    private static final LocalDateTime LATEST = LocalDateTime.of(2026, 9, 5, 0, 0);

    @Test
    void latest_candle_membership_and_values_do_not_depend_on_traversal_order() throws Exception {
        var input = histories("OLD", bars(LATEST.minusYears(2), 3, 10),
                "NEW_A", bars(LATEST, 3, 20), "NEW_B", bars(LATEST, 3, 30));
        var orders = List.of(List.of("OLD", "NEW_A", "NEW_B"),
                List.of("NEW_B", "NEW_A", "OLD"), List.of("NEW_A", "OLD", "NEW_B"));
        for (boolean indexed : List.of(false, true)) {
            for (var order : orders) {
                var scan = new Scan(input, indexed);
                scan.run(order, LatestExploration.class, TimeFrame.Period.DAILY);
                assertEquals(Map.of("NEW_A", 20.0, "NEW_B", 30.0), scan.values());
                assertEquals(order.stream().filter(name -> !name.equals("OLD")).toList(), scan.names());
                assertEquals(indexed ? Map.of("NEW_A", 1, "NEW_B", 1)
                        : Map.of("OLD", 1, "NEW_A", 1, "NEW_B", 1), scan.loads);
                assertEquals(1, scan.completions);
                assertTrue(scan.failures.isEmpty());
                if (indexed)
                    assertEquals(0, scan.resets);
            }
        }
    }

    @Test
    void indexed_scan_publishes_first_row_before_loading_the_next_full_history() throws Exception {
        var scan = new Scan(histories("OLD", bars(LATEST.minusDays(1), 3, 10),
                "NEW_A", bars(LATEST, 3, 20), "NEW_B", bars(LATEST, 3, 30), "EMPTY", bars(LATEST, 0, 0)), true);
        scan.run(List.of("OLD", "NEW_A", "NEW_B", "EMPTY"), LatestExploration.class, TimeFrame.Period.DAILY);
        assertEquals(List.of("NEW_A"), scan.loadsAtPublication.getFirst());
        assertEquals(Map.of("NEW_A", 1, "NEW_B", 1), scan.loads);
        assertEquals(Map.of("OLD", 2, "NEW_A", 3, "NEW_B", 3, "EMPTY", 2), scan.metadataReads());
        assertEquals(List.of("NEW_A", "NEW_B"), scan.names());
        assertEquals(0, scan.resets);
    }

    @Test
    void newest_short_history_removes_older_rows_without_falling_back() throws Exception {
        for (boolean indexed : List.of(false, true)) {
            var scan = new Scan(histories("OLD", bars(LATEST.minusDays(1), 3, 10), "IPO", bars(LATEST, 1, 20)), indexed);
            scan.run(List.of("OLD", "IPO"), LatestExploration.class, TimeFrame.Period.DAILY);
            assertTrue(scan.rows.isEmpty());
            assertEquals(1, scan.completions);
            assertEquals(indexed ? 0 : 1, scan.resets);
            if (indexed)
                assertEquals(Map.of("IPO", 1), scan.loads);
        }
    }

    @Test
    void newest_history_rejected_by_strategy_still_establishes_reference() throws Exception {
        for (boolean indexed : List.of(false, true)) {
            var scan = new Scan(histories("OLD", bars(LATEST.minusDays(1), 3, 10), "INVALID", bars(LATEST, 3, -1)), indexed);
            scan.run(List.of("OLD", "INVALID"), LatestExploration.class, TimeFrame.Period.DAILY);
            assertTrue(scan.rows.isEmpty());
            if (indexed)
                assertEquals(Map.of("INVALID", 1), scan.loads);
        }
    }

    @Test
    void daily_candles_match_by_trading_date_including_exclusive_midnight() throws Exception {
        for (boolean indexed : List.of(false, true)) {
            var scan = new Scan(histories("CLOSE", bars(LATEST.minusHours(7), 3, 10),
                    "MIDNIGHT", bars(LATEST, 3, 20), "PREVIOUS", bars(LATEST.minusDays(1), 3, 30)), indexed);
            scan.run(List.of("CLOSE", "MIDNIGHT", "PREVIOUS"), LatestExploration.class, TimeFrame.Period.DAILY);
            assertEquals(List.of("CLOSE", "MIDNIGHT"), scan.names());
        }
    }

    @Test
    void intraday_candles_match_the_exact_latest_timestamp() throws Exception {
        for (boolean indexed : List.of(false, true)) {
            var scan = new Scan(histories("EARLY", bars(LATEST.minusHours(1), 3, 10),
                    "LATE", bars(LATEST.minusMinutes(5), 3, 20)), indexed);
            scan.run(List.of("EARLY", "LATE"), LatestExploration.class, TimeFrame.Period.M5);
            assertEquals(List.of("LATE"), scan.names());
        }
    }

    @Test
    void historical_and_singleton_scopes_use_archive_dates_not_the_wall_clock() throws Exception {
        for (boolean indexed : List.of(false, true)) {
            var scan = new Scan(histories("ARCHIVE", bars(LATEST.minusYears(10), 3, 10)), indexed);
            scan.run(List.of("ARCHIVE"), LatestExploration.class, TimeFrame.Period.DAILY);
            assertEquals(List.of("ARCHIVE"), scan.names());
        }
    }

    @Test
    void symbol_filter_runs_once_and_defines_the_reference_universe_before_loading() throws Exception {
        for (boolean indexed : List.of(false, true)) {
            var scan = new Scan(histories("INCLUDED", bars(LATEST.minusDays(1), 3, 10),
                    "EXCLUDED", bars(LATEST, 3, 20)), indexed);
            scan.run(List.of("INCLUDED", "EXCLUDED"), LatestExploration.class, TimeFrame.Period.DAILY);
            assertEquals(List.of("INCLUDED"), scan.names());
            assertEquals(Map.of("INCLUDED", 1), scan.loads);
            assertEquals(Map.of("INCLUDED", 1, "EXCLUDED", 1), scan.exploration.filterCalls);
            assertFalse(scan.metadataReads().containsKey("EXCLUDED"));
        }
    }

    @Test
    void empty_selection_and_empty_histories_complete_without_rows() throws Exception {
        for (boolean indexed : List.of(false, true)) {
            var scan = new Scan(histories("EMPTY", bars(LATEST, 0, 0)), indexed);
            scan.run(List.of(), LatestExploration.class, TimeFrame.Period.DAILY);
            scan.run(List.of("EMPTY"), LatestExploration.class, TimeFrame.Period.DAILY);
            assertTrue(scan.rows.isEmpty());
            assertEquals(2, scan.completions);
        }
    }

    @Test
    void runner_reuse_does_not_retain_the_previous_reference_or_rows() throws Exception {
        for (boolean indexed : List.of(false, true)) {
            var scan = new Scan(histories("NEW", bars(LATEST, 3, 10), "OLD", bars(LATEST.minusYears(1), 3, 20)), indexed);
            scan.run(List.of("NEW"), LatestExploration.class, TimeFrame.Period.DAILY);
            scan.rows.clear();
            scan.run(List.of("OLD"), LatestExploration.class, TimeFrame.Period.DAILY);
            assertEquals(List.of("OLD"), scan.names());
            assertEquals(2, scan.completions);
        }
    }

    @Test
    void fallback_publishes_online_and_resets_before_newer_rows() throws Exception {
        var scan = new Scan(histories("OLD", bars(LATEST.minusDays(2), 3, 10),
                "MIDDLE", bars(LATEST.minusDays(1), 3, 20), "NEW", bars(LATEST, 3, 30)), false);
        scan.run(List.of("OLD", "MIDDLE", "NEW"), LatestExploration.class, TimeFrame.Period.DAILY);
        assertEquals(List.of("OLD"), scan.loadsAtPublication.getFirst());
        assertEquals(List.of("row:OLD", "reset", "row:MIDDLE", "reset", "row:NEW", "finished"), scan.events);
        assertEquals(List.of("NEW"), scan.names());
    }

    @Test
    void one_unsupported_resource_uses_online_fallback_for_the_whole_universe() throws Exception {
        var scan = new Scan(histories("OLD", bars(LATEST.minusDays(1), 3, 10), "NEW", bars(LATEST, 3, 20)), true);
        scan.provider.unsupported.add("NEW");
        scan.run(List.of("OLD", "NEW"), LatestExploration.class, TimeFrame.Period.DAILY);
        assertEquals(List.of("OLD"), scan.loadsAtPublication.getFirst());
        assertEquals(List.of("NEW"), scan.names());
        assertEquals(1, scan.resets);
        assertTrue(scan.metadataReads().isEmpty());
    }

    @Test
    void fallback_failure_marks_partial_rows_incomplete_and_does_not_complete() {
        var scan = new Scan(histories("OLD", bars(LATEST.minusDays(1), 3, 10)), false);
        var failure = assertThrows(IllegalStateException.class,
                () -> scan.run(List.of("OLD", "MISSING"), LatestExploration.class, TimeFrame.Period.DAILY));
        assertEquals(List.of("OLD"), scan.names());
        assertEquals(List.of(failure), scan.failures);
        assertEquals(0, scan.completions);
    }

    @Test
    void metadata_failure_is_not_interpreted_as_empty_or_an_unsupported_provider() {
        var scan = new Scan(histories("GOOD", bars(LATEST, 3, 10)), true);
        var failure = assertThrows(IllegalStateException.class,
                () -> scan.run(List.of("GOOD", "MISSING"), LatestExploration.class, TimeFrame.Period.DAILY));
        assertTrue(scan.rows.isEmpty());
        assertTrue(scan.loads.isEmpty());
        assertEquals(List.of(failure), scan.failures);
        assertEquals(0, scan.completions);
    }

    @Test
    void changed_loaded_history_fails_before_publishing_that_row() {
        var scan = new Scan(histories("NEW", bars(LATEST, 3, 10)), true);
        scan.beforeLoad = name -> scan.histories.put(name, bars(LATEST.plusDays(1), 3, 20));
        var failure = assertThrows(IllegalStateException.class,
                () -> scan.run(List.of("NEW"), LatestExploration.class, TimeFrame.Period.DAILY));
        assertTrue(failure.getMessage().contains("data changed"));
        assertTrue(scan.rows.isEmpty());
        assertEquals(List.of(failure), scan.failures);
        assertEquals(0, scan.completions);
    }

    @Test
    void final_metadata_check_detects_stale_and_empty_histories_that_advance_during_the_scan() {
        for (var initial : List.of(bars(LATEST.minusDays(1), 3, 10), bars(LATEST, 0, 0))) {
            var scan = new Scan(histories("SKIPPED", initial, "NEW", bars(LATEST, 3, 20)), true);
            scan.afterRow = row -> scan.histories.put("SKIPPED", bars(LATEST.plusDays(1), 3, 30));
            var failure = assertThrows(IllegalStateException.class,
                    () -> scan.run(List.of("SKIPPED", "NEW"), LatestExploration.class, TimeFrame.Period.DAILY));
            assertTrue(failure.getMessage().contains("SKIPPED"));
            assertEquals(List.of("NEW"), scan.names());
            assertEquals(Map.of("NEW", 1), scan.loads);
            assertEquals(Map.of("SKIPPED", 2, "NEW", 3), scan.metadataReads());
            assertEquals(List.of(failure), scan.failures);
            assertEquals(0, scan.completions);
        }
    }

    @Test
    void final_metadata_check_detects_changes_to_already_published_histories() {
        var scan = new Scan(histories("NEW", bars(LATEST, 3, 10)), true);
        scan.afterRow = row -> scan.histories.put("NEW", bars(LATEST.plusDays(1), 3, 20));
        var failure = assertThrows(IllegalStateException.class,
                () -> scan.run(List.of("NEW"), LatestExploration.class, TimeFrame.Period.DAILY));
        assertEquals(List.of("NEW"), scan.names());
        assertEquals(List.of(failure), scan.failures);
        assertEquals(0, scan.completions);
    }

    @Test
    void changed_prices_with_the_same_last_timestamp_fail_before_publication() {
        var scan = new Scan(histories("NEW", bars(LATEST, 3, 10)), true);
        scan.beforeLoad = name -> {
            scan.histories.put(name, bars(LATEST, 3, 20));
            scan.provider.revisions.merge(name, 1, Integer::sum);
        };
        var failure = assertThrows(IllegalStateException.class,
                () -> scan.run(List.of("NEW"), LatestExploration.class, TimeFrame.Period.DAILY));
        assertTrue(failure.getMessage().contains("data changed"));
        assertTrue(scan.rows.isEmpty());
        assertEquals(List.of(failure), scan.failures);
        assertEquals(0, scan.completions);
    }

    @Test
    void final_metadata_check_detects_same_date_revisions_of_skipped_histories() {
        var scan = new Scan(histories("OLD", bars(LATEST.minusDays(1), 3, 10), "NEW", bars(LATEST, 3, 20)), true);
        scan.afterRow = row -> {
            scan.histories.put("OLD", bars(LATEST.minusDays(1), 3, 30));
            scan.provider.revisions.merge("OLD", 1, Integer::sum);
        };
        var failure = assertThrows(IllegalStateException.class,
                () -> scan.run(List.of("OLD", "NEW"), LatestExploration.class, TimeFrame.Period.DAILY));
        assertTrue(failure.getMessage().contains("OLD"));
        assertEquals(List.of("NEW"), scan.names());
        assertEquals(List.of(failure), scan.failures);
        assertEquals(0, scan.completions);
    }

    @Test
    void metadata_work_is_bounded_to_four_workers_reused_for_final_validation() throws Exception {
        var input = new LinkedHashMap<String, CandleSeries>();
        for (int i = 0; i < 20; i++)
            input.put("STOCK_" + i, bars(LATEST, 3, i));
        var scan = new Scan(input, true);
        scan.provider.firstWorkersReady = new CountDownLatch(4);
        scan.run(List.copyOf(input.keySet()), LatestExploration.class, TimeFrame.Period.DAILY);
        assertEquals(4, scan.provider.maxConcurrentReads.get());
        assertEquals(4, scan.provider.threads.stream().filter(thread -> thread != Thread.currentThread()).count());
        assertEquals(60, scan.provider.totalReads.get());
        assertEquals(20, scan.rows.size());
        assertEquals(1, scan.completions);
    }

    @Test
    void default_explorations_keep_per_symbol_dates_and_streaming_without_metadata_queries() throws Exception {
        var scan = new Scan(histories("OLD", bars(LATEST.minusYears(1), 3, 10), "NEW", bars(LATEST, 3, 20)), true);
        scan.run(List.of("OLD", "NEW"), PerSymbolExploration.class, TimeFrame.Period.DAILY);
        assertEquals(List.of("OLD", "NEW"), scan.names());
        assertEquals(List.of("OLD"), scan.loadsAtPublication.getFirst());
        assertTrue(scan.metadataReads().isEmpty());
        assertEquals(0, scan.resets);
    }

    public static class PerSymbolExploration extends Exploration {
        private final Map<String, Integer> filterCalls = new LinkedHashMap<>();

        @Override
        public boolean filter(Symbol symbol) {
            filterCalls.merge(symbol.getName(), 1, Integer::sum);
            return !symbol.getName().equals("EXCLUDED");
        }

        @Override
        public boolean filter(Symbol symbol, CandleSeries series) {
            return !series.isEmpty() && series.get(0).close() >= 0;
        }

        @Override
        public void explore(Symbol symbol, CandleSeries series) {
            addColumn("Close", series.get(0).close());
        }
    }

    public static class LatestExploration extends PerSymbolExploration {
        @Override
        public boolean requiresLatestCandle() {
            return true;
        }
    }

    private static final class Scan extends ExplorationRunner implements ExplorationListener {
        private final Map<String, CandleSeries> histories;
        private final List<ExplorationFragment> rows = new ArrayList<>();
        private final Map<String, Integer> loads = new LinkedHashMap<>();
        private final List<List<String>> loadsAtPublication = new ArrayList<>();
        private final List<String> events = new ArrayList<>();
        private final List<Throwable> failures = new ArrayList<>();
        private final IndexedProvider provider;
        private final boolean indexed;
        private Consumer<String> beforeLoad = name -> { };
        private Consumer<ExplorationFragment> afterRow = row -> { };
        private PerSymbolExploration exploration;
        private int completions;
        private int resets;

        Scan(Map<String, CandleSeries> histories, boolean indexed) {
            this.histories = new ConcurrentHashMap<>(histories);
            this.indexed = indexed;
            this.provider = new IndexedProvider(this.histories);
            addListener(this);
        }

        void run(List<String> names, Class<? extends Exploration> type, TimeFrame timeFrame) throws Exception {
            DataProvider dataProvider = indexed ? provider : DataProvider.EMPTY;
            var symbols = names.stream().map(name -> new Symbol(SymbolIdentity.of(name), dataProvider)).toList();
            var configuration = ImmutableExplorationConfiguration.builder()
                    .symbols(symbols).timeFrame(timeFrame).datasetMinDataPoints(2).build();
            var context = ImmutableLaunchContext.builder().projectDirectory(Path.of("."))
                    .progressHandle(new ProgressHandle() {
                        @Override public void start(int workTotal) { }
                        @Override public void progress(String message, int workDone) { }
                        @Override public void finish() { }
                    }).launcher((directory, target) -> { })
                    .attributes(Map.of(ExplorationConfiguration.KEY.name(), configuration)).build();
            performLaunch(context, type);
        }

        @Override
        protected Exploration createInstance(Class<?> target) throws NoSuchMethodException, InvocationTargetException,
                InstantiationException, IllegalAccessException {
            return exploration = (PerSymbolExploration) super.createInstance(target);
        }

        @Override
        protected CandleSeries loadDataSeries(Symbol symbol, SymbolResource<Candle> resource) {
            String name = symbol.getName();
            loads.merge(name, 1, Integer::sum);
            beforeLoad.accept(name);
            var series = histories.get(name);
            if (series == null)
                throw new IllegalStateException("Missing history: " + name);
            return series;
        }

        @Override
        public void explorationFragmentCreated(ExplorationFragment next) {
            loadsAtPublication.add(List.copyOf(loads.keySet()));
            rows.add(next);
            events.add("row:" + next.symbol().getName());
            afterRow.accept(next);
        }

        @Override
        public void explorationResultsReset() {
            rows.clear();
            resets++;
            events.add("reset");
        }

        @Override
        public void explorationFailed(Throwable failure) {
            failures.add(failure);
            events.add("failed");
        }

        @Override
        public void explorationFinished() {
            completions++;
            events.add("finished");
        }

        List<String> names() {
            return rows.stream().map(row -> row.symbol().getName()).toList();
        }

        Map<String, Double> values() {
            var values = new LinkedHashMap<String, Double>();
            for (var row : rows)
                values.put(row.symbol().getName(), row.columnValues().get("Close").numberValue().doubleValue());
            return values;
        }

        Map<String, Integer> metadataReads() {
            var reads = new LinkedHashMap<String, Integer>();
            provider.reads.forEach((name, count) -> reads.put(name, count.get()));
            return reads;
        }
    }

    private static final class IndexedProvider implements DataProvider, LatestCandleTimeProvider {
        private final Map<String, CandleSeries> histories;
        private final Map<String, AtomicInteger> reads = new ConcurrentHashMap<>();
        private final Map<String, Integer> revisions = new ConcurrentHashMap<>();
        private final Set<String> unsupported = ConcurrentHashMap.newKeySet();
        private final Set<Thread> threads = ConcurrentHashMap.newKeySet();
        private final AtomicInteger concurrentReads = new AtomicInteger();
        private final AtomicInteger maxConcurrentReads = new AtomicInteger();
        private final AtomicInteger totalReads = new AtomicInteger();
        private CountDownLatch firstWorkersReady;

        IndexedProvider(Map<String, CandleSeries> histories) {
            this.histories = histories;
        }

        @Override public String getName() { return "Indexed test histories"; }
        @Override public Lookup getLookup() { return Lookups.singleton(this); }
        @Override public List<SymbolIdentity> listSymbols(SymbolGroup group) { return List.of(); }
        @Override public <T extends Chronological> Flux<T> query(Class<T> type, DataQuery<T> query) { return Flux.empty(); }

        @Override
        public boolean supports(SymbolResource<Candle> resource) {
            return !unsupported.contains(resource.symbol().name());
        }

        @Override
        public Snapshot getLatestCandleSnapshot(SymbolResource<Candle> resource) {
            String name = resource.symbol().name();
            int sequence = totalReads.incrementAndGet();
            reads.computeIfAbsent(name, ignored -> new AtomicInteger()).incrementAndGet();
            threads.add(Thread.currentThread());
            int concurrent = concurrentReads.incrementAndGet();
            maxConcurrentReads.accumulateAndGet(concurrent, Math::max);
            try {
                if (firstWorkersReady != null && sequence <= 4) {
                    firstWorkersReady.countDown();
                    assertTrue(firstWorkersReady.await(5, TimeUnit.SECONDS), "All four metadata workers should start");
                }
                var series = histories.get(name);
                if (series == null)
                    throw new IllegalStateException("Missing metadata: " + name);
                var time = series.isEmpty() ? OptionalLong.empty() : OptionalLong.of(series.get(0).time());
                return new Snapshot(time, revisions.getOrDefault(name, 0));
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(failure);
            } finally {
                concurrentReads.decrementAndGet();
            }
        }
    }

    private static Map<String, CandleSeries> histories(Object... entries) {
        var result = new LinkedHashMap<String, CandleSeries>();
        for (int i = 0; i < entries.length; i += 2)
            result.put((String) entries[i], (CandleSeries) entries[i + 1]);
        return result;
    }

    private static CandleSeries bars(LocalDateTime end, int count, double close) {
        var bars = new ArrayList<Candle>();
        for (int i = 0; i < count; i++)
            bars.add(Candle.of(end.minusDays(i), close));
        Collections.reverse(bars);
        return CandleSeries.of(SymbolResource.of("TEST", TimeFrame.Period.DAILY), bars);
    }
}
