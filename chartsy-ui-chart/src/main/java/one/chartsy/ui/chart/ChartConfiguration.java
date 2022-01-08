package one.chartsy.ui.chart;

import one.chartsy.ui.chart.type.CandlestickChart;
import one.chartsy.ui.chart.type.OHLC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChartConfiguration {

    @Bean(name = {"Candlestick Chart", "candlestickChart"})
    public Chart candlestickChart() {
        return new CandlestickChart();
    }

    @Bean(name = {"OHLC", "ohlcChart"})
    public Chart ohlcChart() {
        return new OHLC();
    }
}
