package one.chartsy.ui.chart;

public record PlotRenderContext(
        ChartContext chartContext,
        String plotKey,
        String legendName,
        int plotOrder,
        int historicalSlots,
        int totalSlots,
        double widthPercent,
        boolean legended) {
}
