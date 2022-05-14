/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.chartfx.dos;

import java.util.LinkedHashSet;
import java.util.Set;

public class AbsorptionClusterDO {
    // ordered from bottom to top
    private final Set<Interval<Double>> bidClusters = new LinkedHashSet<>();
    // ordered from top to bottom
    private final Set<Interval<Double>> askClusters = new LinkedHashSet<>();

    public void addBidCluster(Interval<Double> cluster) {
        bidClusters.add(cluster);
    }

    public void addAskCluster(Interval<Double> cluster) {
        askClusters.add(cluster);
    }

    public Set<Interval<Double>> getBidClusters() {
        return bidClusters;
    }

    public Set<Interval<Double>> getAskClusters() {
        return askClusters;
    }
}
