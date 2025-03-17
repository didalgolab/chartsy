/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.time;

/**
 * Provides information about time coordinates of points in a time series. The
 * {@code Timeline} can be queried to translate coordinates to time points and
 * vice versa.
 *
 * @author Mariusz Bernacki
 *
 */
public interface Timeline {

    /**
     * Returns the chronological ordering of points on this timeline which can be
     * either natural or reverse.
     */
    Chronological.ChronoOrder getOrder();

    /**
     * Returns the length of the timeline.
     */
    int length();

    /**
     * Returns the time for the given x-coordinate.
     *
     * @param x the x-coordinate to examine
     * @return the epoch micro at the given location
     * @throws IndexOutOfBoundsException if the x-coordinate is out of bounds of
     *                                   this dateline
     */
    long getTimeAt(int x);

    /**
     * Returns the location of the given time point. If no exact location exists the
     * method returns a negative value equal to the
     * <i>{@code -(insertion point)-1}</i>. The method must work symmetrically to
     * {@link #getTimeAt(int)}.
     *
     * @param time the time for which to return a coordinate
     * @return the x-coordinate of the time point
     */
    int getTimeLocation(long time);

    /**
     * Compares this timeline to another one by content.
     *
     * @param a a timeline
     * @param b a timeline to be compared with {@code a} for element-wise equality
     * @return {@code true} if the time coordinates equal to each other and
     *         {@code false} otherwise
     */
    static boolean contentEquals(Timeline a, Timeline b) {
        int length = a.length();
        if (length != b.length())
            return false;

        if (a != b)
            for (int i = 0; i < length; i++)
                if (a.getTimeAt(i) != b.getTimeAt(i))
                    return false;

        return true;
    }
}
