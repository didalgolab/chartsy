/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.ml;

import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import smile.regression.RidgeRegression;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

public class SmilePredictors {

//    static class OLS {
//        public static void main(String[] args) {
////            OLSMultipleLinearRegression r = new OLSMultipleLinearRegression();
////            r.newSampleData(new double[] {2,3,5}, new double[][] {{0},{1},{3}});
////            System.out.println(Arrays.toString(r.estimateRegressionParameters()));
////            System.out.println(Arrays.toString(r.estimateResiduals()));
//            Formula formula = Formula.lhs("y");
//            double[][] x = new double[][] {{0},{1},{3}};
//            double[] y = new double[] {2,3,5};
//            DataFrame df = DataFrame.of(x).merge(DoubleVector.of("y", y));
//            LinearModel model = RidgeRegression.fit(formula, df, 1.0);
//            System.out.println(model);
//            System.out.println("Result: " + model.predict(new double[] {3}));
//
////            double[] x = new double[] {10,20};
////            double res = r.estimateRegressionParameters()[0];
////            for (int i = 0; i < x.length; i++)
////                res += x[i] * r.estimateRegressionParameters()[i + 1];
////            System.out.println("Result: " + res);
//        }
//    }

//    public static void main(String[] args) throws IOException {
//        Formula formula = Formula.lhs("y");
//        System.out.println(formula);
//        ThreadLocalRandom r = ThreadLocalRandom.current();
//        double[][] x = new double[1000][10];
//        for (int i = 0; i < x.length; i++)
//            for (int j = 0; j < x[i].length; j++)
//                x[i][j] = r.nextInt(100_000);
//        double[] y = new double[1000];
//        for (int i = 0; i < x.length; i++)
//            y[i] = r.nextInt(1000);
//
//        double[] predictx = new double[x[0].length];
//        Arrays.setAll(predictx, i -> i);
//        OLSMultipleLinearRegression olslr = new OLSMultipleLinearRegression();
//        long startTime = System.nanoTime();
//        double res = 0;
//        //System.in.read();
//        for (int i = 0; i < 200_000 + 20_000; i++) {
//            Collections.shuffle(Arrays.asList(x));
//            DataFrame df = DataFrame.of(x).merge(DoubleVector.of("y", y));
//            //System.out.println(df);
//            res += RidgeRegression.fit(formula, df, 0.1).predict(predictx);
//            //olslr.newSampleData(y, x);
//            //double[] b = olslr.estimateRegressionParameters();
//            //res += b[0];
//            if (i % 20_000 == 0) {
//                System.out.println(i);
//            }
//            if (i == 20_000L)
//                startTime = System.nanoTime();
//        }
//        long elapsedTime = System.nanoTime() - startTime;
//        System.out.println("Elapsed: " + elapsedTime / 200_000L + " ns per iter");
//        System.out.println(res);
//    }

//    // 306 us / 9000 elem
//    // 9,38 ms / 100 000 elems
//    public static void main(String[] args) throws IOException {
//        ThreadLocalRandom r = ThreadLocalRandom.current();
//        double[][] x = new double[1000][10];
//        for (int i = 0; i < x.length; i++)
//            for (int j = 0; j < x[i].length; j++)
//                x[i][j] = r.nextInt(100_000);
//        double[] y = new double[1000];
//        for (int i = 0; i < x.length; i++)
//            y[i] = r.nextInt(1000);
//
//        double[] predictx = new double[x[0].length];
//        Arrays.setAll(predictx, i -> i);
//        OLSMultipleLinearRegression olslr = new OLSMultipleLinearRegression();
//        long startTime = System.nanoTime();
//        double res = 0;
//        //System.in.read();
//        for (int i = 0; i < 100_000 + 1_000; i++) {
//            Collections.shuffle(Arrays.asList(x));
//            //DataFrame df = DataFrame.of(x).merge(DoubleVector.of("y", y));
//            //System.out.println(df);
//            res += new RidgeRegression..Trainer(1.0).train(x, y).predict(predictx);
//            //olslr.newSampleData(y, x);
//            //double[] b = olslr.estimateRegressionParameters();
//            //res += b[0];
//            if (i % 10_000 == 0) {
//                System.out.println(i);
//            }
//            if (i == 1_000L)
//                startTime = System.nanoTime();
//        }
//        long elapsedTime = System.nanoTime() - startTime;
//        System.out.println("Elapsed: " + elapsedTime / 100_000L + " ns per iter");
//        System.out.println(res);
//    }
}
