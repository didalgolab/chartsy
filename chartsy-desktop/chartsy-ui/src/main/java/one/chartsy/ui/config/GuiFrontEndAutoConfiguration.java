/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.config;

import one.chartsy.kernel.boot.config.FrontEndConfiguration;
import one.chartsy.simulation.SimulationResult;
import one.chartsy.ui.chart.Chart;
import one.chartsy.ui.chart.ChartTemplateDefaults;
import one.chartsy.ui.chart.ChartTemplate;
import one.chartsy.ui.chart.type.CandlestickChart;
import one.chartsy.ui.reports.Report;
import one.chartsy.ui.simulation.reports.ReportableSimulationResult;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.*;

@AutoConfiguration
@ConditionalOnBean(FrontEndConfiguration.class)
public class GuiFrontEndAutoConfiguration {

    @Bean
    @Primary
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public ChartTemplate basicChartTemplate() {
        return ChartTemplateDefaults.basicChartTemplate();
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

