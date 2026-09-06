/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.exploration;

import java.lang.reflect.InvocationTargetException;
import java.text.Format;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import one.chartsy.Attribute;
import one.chartsy.Candle;
import one.chartsy.Symbol;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrameHelper;
import one.chartsy.core.event.ListenerList;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.provider.DataProviders;
import one.chartsy.data.provider.LatestCandleTimeProvider;
import one.chartsy.data.provider.LatestCandleTimeProvider.Snapshot;
import one.chartsy.exploration.ui.ExplorationConfiguration;
import one.chartsy.kernel.Exploration;
import one.chartsy.kernel.ExplorationFragment;
import one.chartsy.kernel.ExplorationListener;
import one.chartsy.kernel.ProgressHandle;
import one.chartsy.kernel.runner.LaunchContext;
import one.chartsy.kernel.runner.LaunchException;
import one.chartsy.kernel.runner.LaunchPerformer;
import one.chartsy.time.Chronological;

public class ExplorationRunner implements LaunchPerformer {

    private static final Logger LOG = Logger.getLogger(ExplorationRunner.class.getName());
    private static final int METADATA_WORKERS = 4;
    private final ListenerList<ExplorationListener> listeners = new ListenerList<>(ExplorationListener.class);

    @Override
    public Collection<Attribute<?>> getRequiredConfigurations() {
        return List.of(new Attribute<>(ExplorationConfiguration.KEY, ExplorationConfiguration::currentSnapshot));
    }

    public void addListener(ExplorationListener listener) {
        listeners.addListener(listener);
    }

    public void removeListener(ExplorationListener listener) {
        listeners.removeListener(listener);
    }

    protected Exploration createInstance(Class<?> target) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return (Exploration) target.getConstructor().newInstance();
    }

    protected CandleSeries loadDataSeries(Symbol symbol, SymbolResource<Candle> resource) throws LaunchException {
        return DataProviders.getHistoricalCandles(symbol.getProvider(), resource);
    }

    @Override
    public void performLaunch(LaunchContext context, Class<?> target) throws Exception {
        try {
            ExplorationConfiguration conf = context.getAttribute(ExplorationConfiguration.KEY).orElseThrow();
            Exploration exploration = createInstance(target);
            boolean intraday = TimeFrameHelper.isIntraday(conf.getTimeFrame());
            Format dateFormat = (intraday
                    ? DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    : DateTimeFormatter.ISO_LOCAL_DATE).toFormat();

            if (exploration.requiresLatestCandle()) {
                var candidates = selectCandidates(conf, exploration);
                context.progressHandle().start(candidates.size());
                if (candidates.stream().allMatch(Candidate::supportsMetadata))
                    scanWithFixedReference(candidates, conf, exploration, dateFormat, intraday, context.progressHandle());
                else
                    scanWithAdvancingReference(candidates, conf, exploration, dateFormat, intraday, context.progressHandle());
            } else {
                scanPerSymbol(conf, exploration, dateFormat, context.progressHandle());
            }
            listeners.fire().explorationFinished();
        } catch (Exception | Error failure) {
            listeners.fire().explorationFailed(failure);
            throw failure;
        }
    }

    private List<Candidate> selectCandidates(ExplorationConfiguration conf, Exploration exploration) {
        var candidates = new ArrayList<Candidate>();
        for (var symbol : conf.getSymbols()) {
            if (exploration.filter(symbol)) {
                var resource = SymbolResource.<Candle>of(symbol, conf.getTimeFrame());
                var metadataProvider = symbol.getProvider().getLookup().lookup(LatestCandleTimeProvider.class);
                candidates.add(new Candidate(symbol, resource, metadataProvider));
            }
        }
        return candidates;
    }

    private void scanWithFixedReference(List<Candidate> candidates, ExplorationConfiguration conf,
            Exploration exploration, Format dateFormat, boolean intraday, ProgressHandle progress) throws Exception {
        if (candidates.isEmpty()) {
            listeners.fire().explorationStatusChanged("No symbols selected");
            return;
        }
        listeners.fire().explorationStatusChanged("Reading latest session\u2026");
        int workers = Math.min(METADATA_WORKERS, candidates.size());
        try (var executor = Executors.newFixedThreadPool(workers,
                Thread.ofPlatform().daemon().name("Exploration metadata-", 0).factory())) {
            long metadataStarted = System.nanoTime();
            Snapshot[] snapshots = readSnapshots(candidates, executor, workers);
            long reference = latestPeriod(snapshots, intraday);
            LOG.info("Latest session resolved: " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - metadataStarted)
                    + " ms, " + candidates.size() + " symbols, reference "
                    + (reference == Long.MIN_VALUE ? "none" : formatPeriod(reference, intraday)));
            listeners.fire().explorationStatusChanged(reference == Long.MIN_VALUE
                    ? "No candle data" : "Scanning session " + formatPeriod(reference, intraday));

            int rows = 0;
            for (int i = 0; i < candidates.size(); i++) {
                var candidate = candidates.get(i);
                progress.progress("Exploring " + candidate.symbol().getName(), i);
                var snapshot = snapshots[i];
                var timestamp = snapshot.time();
                if (timestamp.isEmpty() || candlePeriod(timestamp.getAsLong(), intraday) != reference)
                    continue;
                var series = loadVerifiedSeries(candidate, snapshot);
                if (publishRow(candidate.symbol(), series, conf, exploration, dateFormat))
                    rows++;
            }

            // Recheck every source, including empty and stale histories skipped above.
            // Otherwise a newly updated skipped stock could invalidate a successful result.
            Snapshot[] currentSnapshots = readSnapshots(candidates, executor, workers);
            for (int i = 0; i < candidates.size(); i++)
                verifyUnchanged(candidates.get(i), snapshots[i], currentSnapshots[i]);
            reportLatestResult(rows, reference, intraday, candidates.size());
        }
    }

    private static long latestPeriod(Snapshot[] snapshots, boolean intraday) {
        long latest = Long.MIN_VALUE;
        for (var snapshot : snapshots) {
            var timestamp = snapshot.time();
            if (timestamp.isPresent())
                latest = Math.max(latest, candlePeriod(timestamp.getAsLong(), intraday));
        }
        return latest;
    }

    private CandleSeries loadVerifiedSeries(Candidate candidate, Snapshot expected) throws LaunchException {
        var series = loadDataSeries(candidate.symbol(), candidate.resource());
        var loadedTime = series.isEmpty() ? OptionalLong.empty() : OptionalLong.of(series.get(0).time());
        if (!expected.time().equals(loadedTime))
            throw changedData(candidate);
        verifyUnchanged(candidate, expected, candidate.metadataProvider().getLatestCandleSnapshot(candidate.resource()));
        return series;
    }

    private Snapshot[] readSnapshots(List<Candidate> candidates, ExecutorService executor, int workers) throws Exception {
        var snapshots = new Snapshot[candidates.size()];
        var tasks = new ArrayList<Future<?>>(workers);
        // A bounded number of workers share the universe; never enqueue one task per symbol.
        for (int worker = 0; worker < workers; worker++) {
            int first = worker;
            tasks.add(executor.submit(() -> {
                for (int i = first; i < candidates.size(); i += workers) {
                    var candidate = candidates.get(i);
                    snapshots[i] = candidate.metadataProvider().getLatestCandleSnapshot(candidate.resource());
                }
            }));
        }
        try {
            for (var task : tasks)
                task.get();
        } catch (ExecutionException failure) {
            for (var task : tasks)
                task.cancel(true);
            if (failure.getCause() instanceof Exception cause)
                throw cause;
            if (failure.getCause() instanceof Error cause)
                throw cause;
            throw new IllegalStateException(failure.getCause());
        } catch (InterruptedException failure) {
            for (var task : tasks)
                task.cancel(true);
            Thread.currentThread().interrupt();
            throw failure;
        }
        return snapshots;
    }

    private static void verifyUnchanged(Candidate candidate, Snapshot expected, Snapshot actual) {
        if (!expected.equals(actual))
            throw changedData(candidate);
    }

    private static IllegalStateException changedData(Candidate candidate) {
        return new IllegalStateException("Candle data changed while exploring " + candidate.symbol().getName()
                + ". Results are incomplete; run the exploration again.");
    }

    private void scanWithAdvancingReference(List<Candidate> candidates, ExplorationConfiguration conf,
            Exploration exploration, Format dateFormat, boolean intraday, ProgressHandle progress) throws Exception {
        listeners.fire().explorationStatusChanged("Scanning; the latest session may advance");
        long reference = Long.MIN_VALUE;
        int rows = 0;
        for (int i = 0; i < candidates.size(); i++) {
            var candidate = candidates.get(i);
            progress.progress("Exploring " + candidate.symbol().getName(), i);
            var series = loadDataSeries(candidate.symbol(), candidate.resource());
            if (series.isEmpty())
                continue;
            long latest = candlePeriod(series.get(0).time(), intraday);
            if (latest < reference)
                continue;
            if (latest > reference) {
                reference = latest;
                if (rows > 0)
                    listeners.fire().explorationResultsReset();
                rows = 0;
                listeners.fire().explorationStatusChanged("Scanning session " + formatPeriod(reference, intraday)
                        + "; the reference may advance");
            }
            if (publishRow(candidate.symbol(), series, conf, exploration, dateFormat))
                rows++;
        }
        reportLatestResult(rows, reference, intraday, candidates.size());
    }

    private void scanPerSymbol(ExplorationConfiguration conf, Exploration exploration,
            Format dateFormat, ProgressHandle progress) throws LaunchException {
        progress.start(conf.getSymbols().size());
        listeners.fire().explorationStatusChanged("Scanning");
        int scanned = 0;
        for (var symbol : conf.getSymbols()) {
            progress.progress("Exploring " + symbol.getName(), scanned++);
            if (exploration.filter(symbol)) {
                var series = loadDataSeries(symbol, SymbolResource.of(symbol, conf.getTimeFrame()));
                publishRow(symbol, series, conf, exploration, dateFormat);
            }
        }
    }

    private boolean publishRow(Symbol symbol, CandleSeries series, ExplorationConfiguration conf,
            Exploration exploration, Format dateFormat) {
        if (!exploration.filter(symbol, series) || series.length() < conf.getDatasetMinDataPoints())
            return false;
        ExplorationFragment.Builder row = exploration.addResultFragment(symbol);
        row.addColumn("Symbol", symbol.getName());
        row.addColumn("Date/Time", series.get(0).instant(), dateFormat);
        exploration.explore(symbol, series);
        listeners.fire().explorationFragmentCreated(row.build());
        return true;
    }

    private static long candlePeriod(long timestamp, boolean intraday) {
        return intraday ? timestamp : Chronological.toDateTime(timestamp - 1).toLocalDate().toEpochDay();
    }

    private static String formatPeriod(long period, boolean intraday) {
        return intraday ? Chronological.toDateTime(period).toString() : LocalDate.ofEpochDay(period).toString();
    }

    private void reportLatestResult(int rows, long reference, boolean intraday, int selectedCount) {
        if (reference == Long.MIN_VALUE)
            return;
        var date = formatPeriod(reference, intraday);
        listeners.fire().explorationStatusChanged(rows + " results at " + date);
        LOG.info("Latest candle exploration: " + rows + " rows at " + date
                + " from " + selectedCount + " selected symbols");
    }

    private record Candidate(Symbol symbol, SymbolResource<Candle> resource, LatestCandleTimeProvider metadataProvider) {
        boolean supportsMetadata() {
            return metadataProvider != null && metadataProvider.supports(resource);
        }
    }
}
