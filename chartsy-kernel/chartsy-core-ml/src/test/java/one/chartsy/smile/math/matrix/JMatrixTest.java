/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package one.chartsy.smile.math.matrix;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * @author Haifeng Li
 */
class JMatrixTest {

    double[][] A = {
            {0.9000, 0.4000, 0.0000},
            {0.4000, 0.5000, 0.3000},
            {0.0000, 0.3000, 0.8000}
    };
    double[] b = {0.5, 0.5, 0.5};
    double[][] C = {
            {0.97, 0.56, 0.12},
            {0.56, 0.50, 0.39},
            {0.12, 0.39, 0.73}
    };

    JMatrix matrix = new JMatrix(A);

    /**
     * Test of nrows method, of class ColumnMajorMatrix.
     */
    @Test
    public void testNrows() {
        System.out.println("nrows");
        assertEquals(3, matrix.nrows());
    }

    /**
     * Test of ncols method, of class ColumnMajorMatrix.
     */
    @Test
    public void testNcols() {
        System.out.println("ncols");
        assertEquals(3, matrix.ncols());
    }

    /**
     * Test of colMean method, of class Math.
     */
    @Test
    public void testColMeans() {
        System.out.println("colMeans");
        double[][] A = {
                {0.7220180, 0.07121225, 0.6881997},
                {-0.2648886, -0.89044952, 0.3700456},
                {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] r = {-0.06067647, -0.12325383, 0.56076753};

        double[] result = new JMatrix(A).colMeans();
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7);
        }
    }

    /**
     * Test of rowMean method, of class Math.
     */
    @Test
    public void testRowMeans() {
        System.out.println("rowMeans");
        double[][] A = {
                {0.7220180, 0.07121225, 0.6881997},
                {-0.2648886, -0.89044952, 0.3700456},
                {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] r = {0.4938100, -0.2617642, 0.1447914};

        double[] result = new JMatrix(A).rowMeans();
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7);
        }
    }

    /**
     * Test of get method, of class ColumnMajorMatrix.
     */
    @Test
    public void testGet() {
        System.out.println("get");
        assertEquals(0.9, matrix.get(0, 0), 1E-7);
        assertEquals(0.8, matrix.get(2, 2), 1E-7);
        assertEquals(0.5, matrix.get(1, 1), 1E-7);
        assertEquals(0.0, matrix.get(2, 0), 1E-7);
        assertEquals(0.0, matrix.get(0, 2), 1E-7);
        assertEquals(0.4, matrix.get(0, 1), 1E-7);
    }

    /**
     * Test of ax method, of class ColumnMajorMatrix.
     */
    @Test
    public void testAx() {
        System.out.println("ax");
        double[] d = new double[matrix.nrows()];
        matrix.ax(b, d);
        assertEquals(0.65, d[0], 1E-10);
        assertEquals(0.60, d[1], 1E-10);
        assertEquals(0.55, d[2], 1E-10);
    }

    /**
     * Test of atx method, of class ColumnMajorMatrix.
     */
    @Test
    public void testAtx() {
        System.out.println("atx");
        double[] d = new double[matrix.nrows()];
        matrix.atx(b, d);
        assertEquals(0.65, d[0], 1E-10);
        assertEquals(0.60, d[1], 1E-10);
        assertEquals(0.55, d[2], 1E-10);
    }

    /**
     * Test of AAT method, of class ColumnMajorMatrix.
     */
    @Test
    public void testAAT() {
        System.out.println("AAT");
        JMatrix c = matrix.aat();
        assertEquals(c.nrows(), 3);
        assertEquals(c.ncols(), 3);
        for (int i = 0; i < C.length; i++) {
            for (int j = 0; j < C[i].length; j++) {
                assertEquals(C[i][j], c.get(i, j), 1E-7);
            }
        }
    }

    /**
     * Test of plusEquals method, of class JMatrix.
     */
    @Test
    public void testAdd() {
        System.out.println("add");
        double[][] A = {
                {0.7220180, 0.07121225, 0.6881997},
                {-0.2648886, -0.89044952, 0.3700456},
                {-0.6391588, 0.44947578, 0.6240573}
        };
        double[][] B = {
                {0.6881997, -0.07121225, 0.7220180},
                {0.3700456, 0.89044952, -0.2648886},
                {0.6240573, -0.44947578, -0.6391588}
        };
        double[][] C = {
                {1.4102177, 0, 1.4102177},
                {0.1051570, 0, 0.1051570},
                {-0.0151015, 0, -0.0151015}
        };
        JMatrix a = new JMatrix(A);
        JMatrix b = new JMatrix(B);
        JMatrix c = a.add(b);
        assertTrue(one.chartsy.smile.math.Math.equals(C, c.array(), 1E-7));
    }

    /**
     * Test of minusEquals method, of class JMatrix.
     */
    @Test
    public void testSub() {
        System.out.println("sub");
        double[][] A = {
                {0.7220180, 0.07121225, 0.6881997},
                {-0.2648886, -0.89044952, 0.3700456},
                {-0.6391588, 0.44947578, 0.6240573}
        };
        double[][] B = {
                {0.6881997, -0.07121225, 0.7220180},
                {0.3700456, 0.89044952, -0.2648886},
                {0.6240573, -0.44947578, -0.6391588}
        };
        double[][] C = {
                {0.0338183, 0.1424245, -0.0338183},
                {-0.6349342, -1.7808990, 0.6349342},
                {-1.2632161, 0.8989516, 1.2632161}
        };
        JMatrix a = new JMatrix(A);
        JMatrix b = new JMatrix(B);
        JMatrix c = a.sub(b);
        assertTrue(one.chartsy.smile.math.Math.equals(C, c.array(), 1E-7));
    }

    /**
     * Test of mm method, of class ColumnMajorMatrix.
     */
    @Test
    public void testMm() {
        System.out.println("mm");
        double[][] A = {
                {0.7220180, 0.07121225, 0.6881997},
                {-0.2648886, -0.89044952, 0.3700456},
                {-0.6391588, 0.44947578, 0.6240573}
        };
        double[][] B = {
                {0.6881997, -0.07121225, 0.7220180},
                {0.3700456, 0.89044952, -0.2648886},
                {0.6240573, -0.44947578, -0.6391588}
        };
        double[][] C = {
                {0.9527204, -0.2973347, 0.06257778},
                {-0.2808735, -0.9403636, -0.19190231},
                {0.1159052, 0.1652528, -0.97941688}
        };
        double[][] D = {
                { 0.9887140,  0.1482942, -0.0212965},
                { 0.1482942, -0.9889421, -0.0015881},
                {-0.0212965, -0.0015881, -0.9997719 }
        };
        double[][] E = {
                {0.0000,  0.0000, 1.0000},
                {0.0000, -1.0000, 0.0000},
                {1.0000,  0.0000, 0.0000}
        };

        JMatrix a = new JMatrix(A);
        JMatrix b = new JMatrix(B);
        assertTrue(one.chartsy.smile.math.Math.equals(a.abmm(b).array(), C, 1E-7));
        assertTrue(one.chartsy.smile.math.Math.equals(a.abtmm(b).array(), D, 1E-7));
        assertTrue(one.chartsy.smile.math.Math.equals(a.atbmm(b).array(), E, 1E-7));
    }
}
