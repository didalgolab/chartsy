package one.chartsy.charting.renderers.internal;

import java.util.stream.IntStream;
import java.util.function.IntToDoubleFunction;

import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.data.DefaultDataSet;
import one.chartsy.charting.renderers.PolylineChartRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XStackedDataSetsTest {

    @Test
    void constructor_nan_x_values_are_rejected() {
        var sourceDataSet = sourceDataSet(new double[] { Double.NaN }, new double[] { 1.0 });

        assertRejectsNaNXValues(() -> newStackedDataSets(sourceDataSet));
    }

    @Test
    void setData_nan_x_after_attach_leaves_stacked_view_unchanged() {
        var sourceDataSet = sourceDataSet(new double[] { 1.0 }, new double[] { 1.0 });
        var stackedDataSets = newStackedDataSets(sourceDataSet);

        assertRejectsNaNXValues(() -> sourceDataSet.setData(0, Double.NaN, 1.0));

        assertStackedView(stackedDataSets.getStackedDataSets()[0], new double[] { 1.0 }, new double[] { 1.0 });
    }

    @Test
    void dataSourceChangesEnding_batched_nan_x_update_leaves_stacked_view_unchanged() {
        var sourceDataSet = sourceDataSet(new double[] { 1.0 }, new double[] { 1.0 });
        var stackedDataSets = newStackedDataSets(sourceDataSet);

        stackedDataSets.dataSourceChangesStarting();
        sourceDataSet.addData(2.0, 2.0);
        sourceDataSet.setData(1, Double.NaN, 2.0);

        assertRejectsNaNXValues(stackedDataSets::dataSourceChangesEnding);

        assertStackedView(stackedDataSets.getStackedDataSets()[0], new double[] { 1.0 }, new double[] { 1.0 });
    }

    private static XStackedDataSets newStackedDataSets(DefaultDataSet sourceDataSet) {
        return new XStackedDataSets(
                new DataSet[] { sourceDataSet },
                new PolylineChartRenderer(),
                false,
                false);
    }

    private static DefaultDataSet sourceDataSet(double[] xValues, double[] yValues) {
        return new DefaultDataSet("series", xValues, yValues, true);
    }

    private static void assertRejectsNaNXValues(Executable executable) {
        var thrown = assertThrows(IllegalArgumentException.class, executable);

        assertThat(thrown).hasMessage("X values must not be NaN");
    }

    private static void assertStackedView(DataSet dataSet, double[] expectedXValues, double[] expectedYValues) {
        assertAll(
                () -> assertThat(dataSet.size()).isEqualTo(expectedXValues.length),
                () -> assertThat(extractValues(dataSet, dataSet::getXData)).containsExactly(expectedXValues),
                () -> assertThat(extractValues(dataSet, dataSet::getYData)).containsExactly(expectedYValues)
        );
    }

    private static double[] extractValues(DataSet dataSet, IntToDoubleFunction extractor) {
        return IntStream.range(0, dataSet.size())
                .mapToDouble(extractor)
                .toArray();
    }
}
