/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui;

import com.google.gson.JsonObject;
import one.chartsy.Candle;
import one.chartsy.Symbol;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.ui.chart.*;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

import java.util.List;

@ServiceProvider(service = ChartManager.class)
public class ChartManager {
    private static final ChartFrameCustomizer NONE = __ -> {};
    private static final String DEFAULT_CHART_TYPE = "Candle Stick";

    public ChartTopComponent open(Symbol symbol, TimeFrame period, JsonObject chartOptions) {
        return open(symbol.getProvider(), SymbolResource.of(symbol, period), toChartOpenOptions(chartOptions), NONE);
    }

    public ChartTopComponent open(Symbol symbol, TimeFrame period, ChartOpenOptions options) {
        return open(symbol.getProvider(), SymbolResource.of(symbol, period), options, NONE);
    }

    public ChartTopComponent open(List<Symbol> list, TimeFrame period) {
        return open(list, period, ChartOpenOptions.DEFAULT);
    }

    public ChartTopComponent open(List<Symbol> list, TimeFrame period, JsonObject chartOptions) {
        return open(list, period, toChartOpenOptions(chartOptions));
    }

    public ChartTopComponent open(List<Symbol> list, TimeFrame period, ChartOpenOptions options) {
        if (list.isEmpty())
            throw new IllegalArgumentException("Symbol list is empty");

        Symbol first = list.iterator().next();
        return open(first.getProvider(), SymbolResource.of(first, period), options, chartFrame -> {
            if (list.size() > 1)
                chartFrame.setHistory(new ChartHistory(list, period));
        });
    }

    public ChartTopComponent open(DataProvider provider, SymbolResource<Candle> resource, JsonObject chartOptions, ChartFrameCustomizer customizer) {
        return open(provider, resource, toChartOpenOptions(chartOptions), customizer);
    }

    public ChartTopComponent open(DataProvider provider, SymbolResource<Candle> resource, ChartOpenOptions options, ChartFrameCustomizer customizer) {
        ChartOpenOptions resolvedOptions = (options != null) ? options : ChartOpenOptions.DEFAULT;
        ChartTemplateCatalog.LoadedTemplate loadedTemplate = ChartTemplateCatalog.getDefault()
                .resolveTemplate(resolvedOptions.templateKey());
        String chartType = resolvedOptions.chartTypeNameOrDefault(DEFAULT_CHART_TYPE);

        ChartData chartData = new ChartData();
        chartData.setDataProvider(provider);
        chartData.setSymbol(resource.symbol());
        chartData.setTimeFrame(resource.timeFrame());
        chartData.setChart(one.chartsy.ui.chart.ChartManager.getDefault().getChart(chartType));

        ChartFrame chartFrame = new ChartFrame();
        chartFrame.setChartData(chartData);
        chartFrame.applyLoadedTemplate(loadedTemplate);
        customizer.customize(chartFrame);

        ChartTopComponent tc = new ChartTopComponent(chartFrame);
        tc.open();
        tc.requestActive();
        return tc;
    }

    private static ChartOpenOptions toChartOpenOptions(JsonObject chartOptions) {
        if (chartOptions == null)
            return ChartOpenOptions.DEFAULT;

        String chartType = chartOptions.has("ChartType")
                ? chartOptions.get("ChartType").getAsString()
                : null;
        return new ChartOpenOptions(chartType, null);
    }
}
