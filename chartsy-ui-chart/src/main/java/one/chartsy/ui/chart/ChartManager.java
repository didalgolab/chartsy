package one.chartsy.ui.chart;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@ServiceProvider(service = ChartManager.class)
public class ChartManager {
    private volatile Map<String, Chart> standardCharts;

    public Chart getChart(String name) {
        var chart = getStandardCharts().get(name);
        if (chart == null)
            throw new IllegalArgumentException("Chart `" + name + "` not found");

        return chart;
    }

    protected Map<String, Chart> getStandardCharts() {
        var charts = standardCharts;
        if (charts == null)
            charts = standardCharts = loadStandardCharts();

        return charts;
    }

    protected Map<String, Chart> loadStandardCharts() {
        var charts = new HashMap<String, Chart>();
        for (Chart chart : Lookup.getDefault().lookupAll(Chart.class))
            charts.put(chart.getName(), chart);

        return charts;
    }

    public static ChartManager getDefault() {
        return LazyHolder.INSTANCE;
    }

    private static final class LazyHolder {
        private static final ChartManager INSTANCE = Optional.ofNullable(Lookup.getDefault().lookup(ChartManager.class)).orElseGet(ChartManager::new);
    }

    public static class TypeAdapter extends com.google.gson.TypeAdapter<Chart> {

        @Override
        public void write(JsonWriter out, Chart chart) throws IOException {
            if (chart == null)
                out.nullValue();
            else
                out.value(chart.getName());
        }

        @Override
        public Chart read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return getDefault().getChart(in.nextString());
        }
    }
}
