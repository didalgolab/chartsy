/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.collections;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

public class DoubleArrayStatisticsTimeSeries extends AbstractMap<LocalDateTime, DoubleArrayStatistics> {

    private final Map<LocalDateTime, DoubleArrayStatistics> timeSeriesBins = Collections.synchronizedMap(new TreeMap<>());
    private final long binSizeInNanos;
    private final DoubleArray latestBinValues;
    private volatile long latestBinEndTime;

    public DoubleArrayStatisticsTimeSeries(Duration binSize) {
        binSizeInNanos = binSize.toNanos();
        latestBinValues = new DoubleArray();
        latestBinEndTime = System.nanoTime() + binSizeInNanos;
    }

    public void add(double value) {
        synchronized (this) {
            long currentTime = System.nanoTime();
            if (currentTime > latestBinEndTime) {
                timeSeriesBins.put(LocalDateTime.now(), latestBinValues.sortAndComputeStatistics());
                latestBinValues.clear();
                latestBinEndTime = currentTime + binSizeInNanos;
            }
            latestBinValues.add(value);
        }
    }

    @Override
    public Set<Entry<LocalDateTime, DoubleArrayStatistics>> entrySet() {
        return timeSeriesBins.entrySet();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        forEach((key, value) -> {
            sb.append('"').append(key).append("\": ").append(value).append('\n');
        });
        return sb.toString();
    }
}
