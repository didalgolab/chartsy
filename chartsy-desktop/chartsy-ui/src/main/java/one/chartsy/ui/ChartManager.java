/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui;

import com.google.gson.JsonObject;
import one.chartsy.Candle;
import one.chartsy.Symbol;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.ui.chart.ChartData;
import one.chartsy.ui.chart.ChartFrame;
import one.chartsy.ui.chart.ChartFrameCustomizer;
import one.chartsy.ui.chart.ChartHistory;
import one.chartsy.ui.chart.ChartTemplateCatalog;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.lookup.ServiceProvider;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.Objects;

import static one.chartsy.ui.chart.ChartManager.getDefault;

@ServiceProvider(service = ChartManager.class)
public class ChartManager {
    private static final Logger log = LogManager.getLogger(ChartManager.class);
    private static final ChartFrameCustomizer NONE = __ -> {};
    private static final String DEFAULT_CHART_TYPE = "Candle Stick";

    public ChartTopComponent open(Symbol symbol, TimeFrame period, JsonObject chartOptions) {
        return open(symbol.getProvider(), SymbolResource.of(symbol, period), toChartOpenOptions(chartOptions), NONE);
    }

    public ChartTopComponent open(Symbol symbol, TimeFrame period) {
        return open(symbol, period, ChartOpenOptions.DEFAULT);
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

        Symbol first = list.getFirst();
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
        TemplateResolution templateResolution = resolveLoadedTemplate(resolvedOptions, ChartTemplateCatalog.getDefault());
        ChartTemplateCatalog.LoadedTemplate loadedTemplate = templateResolution.loadedTemplate();
        String chartType = resolvedOptions.chartTypeNameOrDefault(DEFAULT_CHART_TYPE);

        ChartData chartData = new ChartData();
        chartData.setDataProvider(provider);
        chartData.setSymbol(resource.symbol());
        chartData.setTimeFrame(resource.timeFrame());
        chartData.setChart(getDefault().getChart(chartType));

        ChartFrame chartFrame = new ChartFrame();
        chartFrame.setChartData(chartData);
        chartFrame.applyLoadedTemplate(loadedTemplate);
        customizer.customize(chartFrame);

        ChartTopComponent tc = new ChartTopComponent(chartFrame);
        tc.open();
        tc.requestActive();
        if (templateResolution.warningMessage() != null)
            notifyTemplateFallback(templateResolution.warningMessage());
        return tc;
    }

    static TemplateResolution resolveLoadedTemplate(ChartOpenOptions options, ChartTemplateCatalog catalog) {
        ChartOpenOptions resolvedOptions = (options != null) ? options : ChartOpenOptions.DEFAULT;
        try {
            return new TemplateResolution(catalog.resolveTemplate(resolvedOptions.templateKey()), null);
        } catch (IllegalArgumentException ex) {
            log.warn("Requested chart template `{}` is no longer available. Falling back to the default template.",
                    resolvedOptions.templateKey(), ex);
            try {
                return new TemplateResolution(catalog.getDefaultTemplate(), null);
            } catch (RuntimeException defaultEx) {
                return degradedTemplateResolution(resolvedOptions, defaultEx);
            }
        } catch (RuntimeException ex) {
            return degradedTemplateResolution(resolvedOptions, ex);
        }
    }

    private static TemplateResolution degradedTemplateResolution(ChartOpenOptions options, RuntimeException ex) {
        log.warn("Falling back to the built-in chart template while opening a chart", ex);
        String templateName = (options != null && options.templateKey() != null)
                ? "requested"
                : "default";
        String message = "The " + templateName + " chart template could not be loaded. "
                + "The chart opened with the built-in template instead.";
        if (ex.getMessage() != null && !ex.getMessage().isBlank())
            message += "\n" + ex.getMessage();
        return new TemplateResolution(ChartTemplateCatalog.builtInTemplate(), message);
    }

    private static void notifyTemplateFallback(String message) {
        if (GraphicsEnvironment.isHeadless())
            return;

        NotifyDescriptor descriptor = new NotifyDescriptor.Message(message, NotifyDescriptor.WARNING_MESSAGE);
        SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(descriptor));
    }

    record TemplateResolution(ChartTemplateCatalog.LoadedTemplate loadedTemplate, String warningMessage) {
        TemplateResolution {
            loadedTemplate = Objects.requireNonNull(loadedTemplate, "loadedTemplate");
        }
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
