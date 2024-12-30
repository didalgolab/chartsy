package one.chartsy.analysis.hypothesis.testing;

import one.chartsy.data.RealVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VarianceCITest {

    @Test
    void varianceCI() {
        RealVector ci = VarianceCI.varianceCI(RealVector.fromValues(1, 2, 4, 6, 3));

        assertEquals(1.32815, ci.get(0), 0.00001);
        assertEquals(30.5521, ci.get(1), 0.00001);
    }
}