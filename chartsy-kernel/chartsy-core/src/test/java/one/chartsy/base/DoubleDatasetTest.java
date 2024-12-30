package one.chartsy.base;

import one.chartsy.base.dataset.ImmutableDoubleDataset;
import one.chartsy.finance.FinancialIndicators;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DoubleDatasetTest {

    private final Random random = new Random();

    @Test
    void fdi_computes_withRandomData() {
        int dataSize = 100;
        double[] data = new double[dataSize];
        for (int i = 0; i < dataSize; i++) {
            data[i] = random.nextDouble() * 100;
        }
        DoubleDataset series = ImmutableDoubleDataset.of(data);

//        int periods = 5;
//        var expected = FinancialIndicators.fdi()
//        for (int i = periods - 1; i < dataSize - periods; i++) {
//            double expected = FinancialIndicators.fdi(series, periods).get(i - periods + 1);
//            double actual = series.fdi(periods, i);
//            assertEquals(expected, actual, 1e-6, "FDI values should be equal at index " + i);
//        }
    }
}