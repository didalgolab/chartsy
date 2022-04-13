package one.chartsy.exploration;

import one.chartsy.*;
import one.chartsy.core.event.ListenerList;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.provider.DataProviders;
import one.chartsy.exploration.ui.ExplorationConfiguration;
import one.chartsy.kernel.Exploration;
import one.chartsy.kernel.ExplorationFragment;
import one.chartsy.kernel.ExplorationListener;
import one.chartsy.kernel.ProgressHandle;
import one.chartsy.kernel.runner.LaunchContext;
import one.chartsy.kernel.runner.LaunchException;
import one.chartsy.kernel.runner.LaunchPerformer;

import java.lang.reflect.InvocationTargetException;
import java.text.Format;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

public class ExplorationRunner implements LaunchPerformer {

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
        ExplorationConfiguration conf = context.getAttribute(ExplorationConfiguration.KEY).orElseThrow();
        List<Symbol> symbols = conf.getSymbols();
        TimeFrame timeFrame = conf.getTimeFrame();
        Format dateTimeFormat = (TimeFrameHelper.isIntraday(timeFrame)
                ? DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                : DateTimeFormatter.ISO_LOCAL_DATE).toFormat();

        ProgressHandle ph = context.progressHandle();
        ph.start(symbols.size());

        int k = 0;
        Exploration exploration = createInstance(target);
        for (Symbol symbol : symbols) {

            ph.progress("Explorating " + symbol.getName(), k++);
            if (!exploration.filter(symbol))
                continue;

            SymbolResource<Candle> resource = SymbolResource.of(symbol, conf.getTimeFrame());
            CandleSeries series = loadDataSeries(symbol, resource);
            if (!exploration.filter(symbol, series))
                continue;

            if (series.length() >= conf.getDatasetMinDataPoints()) {
                ExplorationFragment.Builder rowFragment = exploration.addResultFragment(symbol);
                rowFragment.addColumn("Symbol", symbol.getName());
                rowFragment.addColumn("Date/Time", series.get(0).getDateTime(), dateTimeFormat);
                exploration.explore(symbol, series);

                listeners.fire().explorationFragmentCreated(rowFragment.build());
            }
        }
        listeners.fire().explorationFinished();
    }
}
