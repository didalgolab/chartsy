/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
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

    /**
     * To restore the max-heap condition when a node's priority is increased.
     * We move up the heap, exchaning the node at position k with its parent
     * (at postion k/2) if necessary, continuing as long as a[k/2] &lt; a[k] or
     * until we reach the top of the heap.
     */
    public static void siftUp(double[] arr, int k) {
        while (k > 1 && arr[k/2] < arr[k]) {
            swap(arr, k, k/2);
            k = k/2;
        }
    }

    /**
     * To restore the max-heap condition when a node's priority is increased.
     * We move up the heap, exchaning the node at position k with its parent
     * (at postion k/2) if necessary, continuing as long as a[k/2] &lt; a[k] or
     * until we reach the top of the heap.
     */
    public static <T extends Comparable<? super T>> void siftUp(T[] arr, int k) {
        while (k > 1 && arr[k/2].compareTo(arr[k]) < 0) {
            swap(arr, k, k/2);
            k = k/2;
        }
    }

    /**
     * To restore the max-heap condition when a node's priority is decreased.
     * We move down the heap, exchanging the node at position k with the larger
     * of that node's two children if necessary and stopping when the node at
     * k is not smaller than either child or the bottom is reached. Note that
     * if n is even and k is n/2, then the node at k has only one child -- this
     * case must be treated properly.
     */
    public static void siftDown(double[] arr, int k, int n) {
        while (2*k <= n) {
            int j = 2 * k;
            if (j < n && arr[j] < arr[j + 1]) {
                j++;
            }
            if (arr[k] >= arr[j]) {
                break;
            }
            swap(arr, k, j);
            k = j;
        }
    }

    /**
     * To restore the max-heap condition when a node's priority is decreased.
     * We move down the heap, exchanging the node at position k with the larger
     * of that node's two children if necessary and stopping when the node at
     * k is not smaller than either child or the bottom is reached. Note that
     * if n is even and k is n/2, then the node at k has only one child -- this
     * case must be treated properly.
     */
    public static <T extends Comparable<? super T>> void siftDown(T[] arr, int k, int n) {
        while (2*k <= n) {
            int j = 2 * k;
            if (j < n && arr[j].compareTo(arr[j + 1]) < 0) {
                j++;
            }
            if (arr[k].compareTo(arr[j]) >= 0) {
                break;
            }
            SortUtils.swap(arr, k, j);
            k = j;
        }
    }
}
