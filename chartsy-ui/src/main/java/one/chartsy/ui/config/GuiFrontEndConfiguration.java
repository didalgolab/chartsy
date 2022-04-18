package one.chartsy.ui.config;

import com.google.gson.GsonBuilder;
import one.chartsy.kernel.boot.config.FrontEndConfiguration;
import one.chartsy.kernel.ServiceManager;
import one.chartsy.kernel.ServiceManagerTypeAdapter;
import one.chartsy.simulation.SimulationResult;
import one.chartsy.ui.chart.Chart;
import one.chartsy.ui.chart.ChartTemplate;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.Overlay;
import one.chartsy.ui.chart.type.CandlestickChart;
import one.chartsy.ui.json.gson.BasicStrokeTypeAdapter;
import one.chartsy.ui.json.gson.ColorTypeAdapter;
import one.chartsy.ui.json.gson.FontTypeAdapter;
import one.chartsy.ui.reports.Report;
import one.chartsy.ui.simulation.reports.ReportableSimulationResult;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.*;

import java.awt.*;

@Configuration
@ConditionalOnBean(FrontEndConfiguration.class)
public class GuiFrontEndConfiguration {

    @Bean
    @Primary
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public ChartTemplate basicChartTemplate() {
        var json = """
                {"name":"Basic Chart","chart":"Candle Stick","chartProperties":{"axisTick":6.0,"axisDateStick":10.0,"axisPriceStick":5.0,"axisColor":"#2e3436","axisStrokeIndex":0,"axisLogarithmicFlag":true,"barWidth":4.0,"barColor":"#2e3436","barStrokeIndex":0,"barVisibility":true,"barDownColor":"#2e3436","barDownVisibility":true,"barUpColor":"#ffffff","barUpVisibility":true,"gridHorizontalColor":"#eeeeec","gridHorizontalStrokeIndex":0,"gridHorizontalVisibility":true,"gridVerticalColor":"#eeeeec","gridVerticalStrokeIndex":0,"gridVerticalVisibility":true,"backgroundColor":"#ffffff","font":"Dialog-PLAIN-12","fontColor":"#2e3436","markerVisibility":false,"toolbarVisibility":true,"toolbarSmallIcons":false,"toolbarShowLabels":true,"annotationLayerVisible":true,"listeners":[]},"overlays":["FRAMA, Leading","FRAMA, Trailing","Sfora","Volume"],"indicators":["Fractal Dimension"]}""";
        var chartTemplate = new GsonBuilder()
                .registerTypeAdapter(Color.class, new ColorTypeAdapter())
                .registerTypeAdapter(Font.class, new FontTypeAdapter())
                .registerTypeAdapter(BasicStroke.class, new BasicStrokeTypeAdapter())
                .registerTypeAdapter(Chart.class, new ServiceManagerTypeAdapter<>(ServiceManager.of(Chart.class)))
                .registerTypeAdapter(Overlay.class, new ServiceManagerTypeAdapter<>(ServiceManager.of(Overlay.class)))
                .registerTypeAdapter(Indicator.class, new ServiceManagerTypeAdapter<>(ServiceManager.of(Indicator.class)))
                .create()
                .fromJson(json, ChartTemplate.class);
        return chartTemplate;
    }

    @Bean(name = {"Candle Stick", "candlestickChart"})
    public Chart candlestickChart() {
        return new CandlestickChart();
    }

    @Bean
    public Report simulationResultReport(SimulationResult result) {
        return new ReportableSimulationResult(result);
    }
}
