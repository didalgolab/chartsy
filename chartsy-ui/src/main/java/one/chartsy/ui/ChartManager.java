package one.chartsy.ui;

import com.google.gson.JsonObject;
import one.chartsy.Candle;
import one.chartsy.Symbol;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.kernel.boot.FrontEnd;
import one.chartsy.ui.chart.*;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

import java.util.List;

@ServiceProvider(service = ChartManager.class)
public class ChartManager {
    private static final ChartFrameCustomizer NONE = __ -> {};

    public ChartTopComponent open(Symbol symbol, TimeFrame period, JsonObject chartOptions) {
        return open(symbol.getProvider(), SymbolResource.of(symbol, period), chartOptions, NONE);
    }

    public ChartTopComponent open(List<Symbol> list, TimeFrame period) {
        return open(list, period, null);
    }

    public ChartTopComponent open(List<Symbol> list, TimeFrame period, JsonObject chartOptions) {
        if (list.isEmpty())
            throw new IllegalArgumentException("Symbol list is empty");

        Symbol first = list.iterator().next();
        return open(first.getProvider(), SymbolResource.of(first, period), chartOptions, chartFrame -> {
            if (list.size() > 1)
                chartFrame.setHistory(new ChartHistory(list, period));
        });
    }

    public ChartTopComponent open(DataProvider provider, SymbolResource<Candle> resource, JsonObject chartOptions, ChartFrameCustomizer customizer) {
        FrontEnd frontEnd = Lookup.getDefault().lookup(FrontEnd.class);
        ChartTemplate template = frontEnd.getApplicationContext().getBean(ChartTemplate.class);
        String chartType = "Candle Stick";
        if (chartOptions != null) {
            if (chartOptions.has("ChartType"))
                chartType = chartOptions.get("ChartType").getAsString();
        }

        ChartData chartData = new ChartData();
        chartData.setDataProvider(provider);
        chartData.setSymbol(resource.symbol());
        chartData.setTimeFrame(resource.timeFrame());
        chartData.setChart(frontEnd.getApplicationContext().getBean(chartType, Chart.class));

        ChartFrame chartFrame = new ChartFrame();
        chartFrame.setChartData(chartData);
        chartFrame.setChartTemplate(template);
        customizer.customize(chartFrame);

        ChartTopComponent tc = new ChartTopComponent(chartFrame);
        tc.open();
        tc.requestActive();
        return tc;
    }
}
