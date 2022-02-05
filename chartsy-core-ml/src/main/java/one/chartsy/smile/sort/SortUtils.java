package one.chartsy.smile.sort;

import one.chartsy.smile.data.Attribute;

public abstract class SortUtils {

    /**
     * Sorts each variable and returns the index of values in ascending order.
     * Only numeric attributes will be sorted. Note that the order of original
     * array is NOT altered.
     *
     * @param x a set of variables to be sorted. Each row is an instance. Each
     * column is a variable.
     * @return the index of values in ascending order
     */
    public static int[][] sort(Attribute[] attributes, double[][] x) {
        int n = x.length;
        int p = x[0].length;

        double[] a = new double[n];
        int[][] index = new int[p][];

        for (int j = 0; j < p; j++) {
            if (attributes[j].getType() == Attribute.Type.NUMERIC) {
                for (int i = 0; i < n; i++) {
                    a[i] = x[i][j];
                }
                index[j] = QuickSort.sort(a);
            }
        }

        return index;
    }

    /**
     * Swap two positions.
     */
    public static void swap(int[] arr, int i, int j) {
        int a = arr[i];
        arr[i] = arr[j];
        arr[j] = a;
    }

    /**
     * Swap two positions.
     */
    public static void swap(float[] arr, int i, int j) {
        float a = arr[i];
        arr[i] = arr[j];
        arr[j] = a;
    }

    /**
     * Swap two positions.
     */
    public static void swap(double[] arr, int i, int j) {
        double a;
        a = arr[i];
        arr[i] = arr[j];
        arr[j] = a;
    }

    /**
     * Swap two positions.
     */
    public static void swap(Object[] arr, int i, int j) {
        Object a;
        a = arr[i];
        arr[i] = arr[j];
        arr[j] = a;
    }
}
