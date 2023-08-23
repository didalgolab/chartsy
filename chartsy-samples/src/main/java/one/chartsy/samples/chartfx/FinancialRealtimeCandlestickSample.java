/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.chartfx;

import static one.chartsy.samples.chartfx.service.StandardTradePlanAttributes.ORDERS;
import static one.chartsy.samples.chartfx.service.StandardTradePlanAttributes.POSITIONS;
import static one.chartsy.samples.chartfx.service.period.IntradayPeriod.IntradayPeriodEnum.M;

import java.util.ArrayList;
import java.util.List;

import javafx.application.Application;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.HPos;
import javafx.scene.Scene;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import de.gsi.chart.Chart;
import de.gsi.chart.XYChart;
import de.gsi.chart.axes.Axis;
import de.gsi.chart.plugins.YRangeIndicator;
import de.gsi.chart.plugins.YWatchValueIndicator;
import de.gsi.chart.renderer.Renderer;
import de.gsi.chart.renderer.spi.financial.AbstractFinancialRenderer;
import de.gsi.chart.renderer.spi.financial.CandleStickRenderer;
import de.gsi.chart.renderer.spi.financial.PositionFinancialRendererPaintAfterEP;
import de.gsi.chart.renderer.spi.financial.css.FinancialColorSchemeConstants;
import de.gsi.chart.renderer.spi.financial.service.RendererPaintAfterEPAware;
import de.gsi.chart.utils.FXUtils;
import de.gsi.dataset.spi.DefaultDataSet;
import de.gsi.dataset.spi.financial.OhlcvDataSet;
import de.gsi.dataset.spi.financial.api.attrs.AttributeModel;
import one.chartsy.samples.chartfx.dos.OrderContainer;
import one.chartsy.samples.chartfx.dos.PositionContainer;
import one.chartsy.samples.chartfx.service.SimpleOhlcvReplayDataSet;
import one.chartsy.samples.chartfx.service.execution.BacktestExecutionPlatform;
import one.chartsy.samples.chartfx.service.execution.BasicOrderExecutionService;
import one.chartsy.samples.chartfx.service.order.PositionFinancialDataSet;
import one.chartsy.samples.chartfx.service.period.IntradayPeriod;
import one.chartsy.samples.chartfx.service.plan.MktOrderListTradePlan;
import one.chartsy.samples.chartfx.service.plan.MktOrderListTradePlan.SimMktOrder;

/**
 * Tick OHLC/V realtime processing. Demonstration of re-sample data to 2M timeframe.
 * Support/Resistance range levels added.
 * YWatchValueIndicator for better visualization of y-values, auto-handling of close prices and manual settings of price levels.
 *
 * @author afischer
 */
public class FinancialRealtimeCandlestickSample extends AbstractBasicFinancialApplication {
    /**
     * Sample App Test Configuration
     */
    @Override
    protected void configureApp() {
        title = "Replay OHLC/V Tick Data in real-time (press 'replay' button, zoom by mousewheel)";
        theme = FinancialColorSchemeConstants.SAND;
        resource = "REALTIME_OHLC_TICK";
        timeRange = "2016/07/29 00:00-2016/07/29 20:15";
        tt = "00:00-23:59"; // time template whole day session
        replayFrom = "2016/07/29 13:58";
        period = new IntradayPeriod(M, 2.0);
    }

    /**
     * Prepare charts to the root.
     */
    protected Scene prepareScene() {
        String priceFormat = "%1.1f";
        // simulate market orders list
        List<SimMktOrder> orders = new ArrayList<>();
        orders.add(new SimMktOrder("2016/07/29 14:06", 3));
        orders.add(new SimMktOrder("2016/07/29 14:15", -1));
        orders.add(new SimMktOrder("2016/07/29 14:24", -1));
        orders.add(new SimMktOrder("2016/07/29 14:36", -1));

        orders.add(new SimMktOrder("2016/07/29 15:10", -3));
        orders.add(new SimMktOrder("2016/07/29 15:38", 3));

        orders.add(new SimMktOrder("2016/07/29 16:39", -3));
        orders.add(new SimMktOrder("2016/07/29 16:44", 1));
        orders.add(new SimMktOrder("2016/07/29 16:56", 1));
        orders.add(new SimMktOrder("2016/07/29 18:40", 1));

        final Chart chart = getDefaultFinancialTestChart(theme);
        final AbstractFinancialRenderer<?> renderer = (AbstractFinancialRenderer<?>) chart.getRenderers().get(0);

        chart.setTitle(title);

        // prepare top financial toolbar with replay support
        ToolBar testVariableToolBar = getTestToolBar(chart, renderer, true);

        // prepare financial y-value indicator
        Axis yAxis = chart.getAxes().get(1);
        if (ohlcvDataSet instanceof SimpleOhlcvReplayDataSet) {
            SimpleOhlcvReplayDataSet replayDataSet = ((SimpleOhlcvReplayDataSet) ohlcvDataSet);
            // close prices visualization
            final YWatchValueIndicator closeIndicator = new YWatchValueIndicator(yAxis, priceFormat);
            //closeIndicator.setPreventOcclusion(true);
            closeIndicator.setId("price");
            closeIndicator.setLineVisible(false);
            closeIndicator.setEditable(false);
            chart.getPlugins().add(closeIndicator);
            replayDataSet.addOhlcvChangeListener(ohlcvItem -> FXUtils.runFX(() -> closeIndicator.setMarkerValue(ohlcvItem.getClose())));

            // define context
            AttributeModel context = new AttributeModel()
                                             .setAttribute(ORDERS, new OrderContainer())
                                             .setAttribute(POSITIONS, new PositionContainer());

            // position/order visualization
            String asset = replayDataSet.getResource(); // just example, it is more complex in real platform
            PositionFinancialDataSet positionFinancialDataSet = new PositionFinancialDataSet(
                    asset, ohlcvDataSet, context);

            // example of addition complex extension-point to renderer
            if (renderer instanceof RendererPaintAfterEPAware) {
                ((RendererPaintAfterEPAware) renderer).addPaintAfterEp(new PositionFinancialRendererPaintAfterEP(positionFinancialDataSet, (XYChart) chart));
            }

            // execution platform (has to be last added to dataset)
            BacktestExecutionPlatform executionPlatform = new BacktestExecutionPlatform();
            executionPlatform.setContext(context);
            executionPlatform.addExecutionPlatformListener(positionFinancialDataSet); // order notification listens position dataset

            // basic handling of orders simple example (MKT only)
            BasicOrderExecutionService orderExecutionService = new BasicOrderExecutionService(context, executionPlatform);

            // create custom trade plan
            MktOrderListTradePlan tradePlan = new MktOrderListTradePlan(context, asset, orderExecutionService, orders);

            // connection OHLC/V listeners
            replayDataSet.addOhlcvChangeListener(tradePlan);
            replayDataSet.addOhlcvChangeListener(executionPlatform); // execution platform listens replay data (has to be last!)
        }

        // manual levels
        chart.getPlugins().add(new YWatchValueIndicator(yAxis, priceFormat, 4727.5));
        chart.getPlugins().add(new YWatchValueIndicator(yAxis, priceFormat, 4715.0));

        // simple S/R ranges
        chart.getPlugins().add(createRsLevel(yAxis, 4710, 4711, "Daily Support"));
        chart.getPlugins().add(createRsLevel(yAxis, 4731, 4733, "Daily Resistance"));

        // apply all changes by addons and extensions
        applyColorScheme(theme, (XYChart) chart);

        VBox root = new VBox();
        VBox.setVgrow(chart, Priority.SOMETIMES);
        root.getChildren().addAll(testVariableToolBar, chart);

        Scene scene = new Scene(root, prefSceneWidth, prefSceneHeight);
//        scene.windowProperty().addListener((observable, oldValue, newValue) -> {
//            if (newValue != null) {
//                chart./*getCanvas().*/scaleXProperty().bind(new SimpleDoubleProperty(1.0).divide(scene.getWindow().outputScaleXProperty()));
//                chart./*getCanvas().*/scaleYProperty().bind(new SimpleDoubleProperty(1.0).divide(scene.getWindow().outputScaleYProperty()));
//            }
//        });
        return scene;
    }

    protected YRangeIndicator createRsLevel(Axis yAxis, double lowerBound, double upperBound, String description) {
        final YRangeIndicator rangeIndi = new YRangeIndicator(yAxis, lowerBound, upperBound, description);
        rangeIndi.setLabelHorizontalAnchor(HPos.LEFT);
        rangeIndi.setLabelHorizontalPosition(0.01);

        return rangeIndi;
    }

    protected void prepareRenderers(XYChart chart, OhlcvDataSet ohlcvDataSet, DefaultDataSet indiSet) {
        // create and apply renderers
        Renderer renderer = new CandleStickRenderer(true);
        renderer.getDatasets().addAll(ohlcvDataSet);

        chart.getRenderers().clear();
        chart.getRenderers().add(renderer);
    }

    /**
     * @param args the command line arguments
     */
    public static void main(final String[] args) {
        Application.launch(args);
    }
}
