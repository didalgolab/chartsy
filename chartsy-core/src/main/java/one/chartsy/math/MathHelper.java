/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.math;

import one.chartsy.collections.IntArrayDeque;

public final class MathHelper {

    public static double[] runmax(double[] a, int w) {
        double[] result = new double[a.length - w + 1];
        runmax(a, a.length, w, result);
        return result;
    }

    public static void runmax(double[] a, int n, int w, double[] b) {
        var q = new IntArrayDeque(w);

        // initialize deque q for first window
        for (int i = 0; i < w; i++) {
            while (!q.isEmpty() && a[i] >= a[q.getLast()])
                q.pollLast();
            q.offerLast(i);
        }

        for (int i = w; i < n; i++) {
            b[i - w] = a[q.getFirst()];

            // update Q for new window
            while (!q.isEmpty() && a[i] >= a[q.getLast()])
                q.pollLast();

            // pop older element outside window from Q
            while (!q.isEmpty() && q.getFirst() <= i - w)
                q.pollFirst();

            // insert current element in Q
            q.offerLast(i);
        }
        b[n - w] = a[q.getFirst()];
    }

    public static double[] runmin(double[] a, int w) {
        double[] result = new double[a.length - w + 1];
        runmin(a, a.length, w, result);
        return result;
    }

    public static void runmin(double[] a, int n, int w, double[] b) {
        var q = new IntArrayDeque(w);

        // initialize deque q for first window
        for (int i = 0; i < w; i++) {
            while (!q.isEmpty() && a[i] <= a[q.getLast()])
                q.pollLast();
            q.offerLast(i);
        }

        for (int i = w; i < n; i++) {
            b[i - w] = a[q.getFirst()];

            // update Q for new window
            while (!q.isEmpty() && a[i] <= a[q.getLast()])
                q.pollLast();

            // pop older element outside window from Q
            while (!q.isEmpty() && q.getFirst() <= i - w)
                q.pollFirst();

            // insert current element in Q
            q.offerLast(i);
        }
        b[n - w] = a[q.getFirst()];
    }
}
