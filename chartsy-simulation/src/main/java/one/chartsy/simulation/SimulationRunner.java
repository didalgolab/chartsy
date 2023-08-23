/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.simulation;

import one.chartsy.data.Series;

import java.util.List;

public interface SimulationRunner {

    default SimulationResult run(Series<?> dataset, SimulationDriver strategy) {
        return run(List.of(dataset), strategy);
    }

    SimulationResult run(List<? extends Series<?>> datasets, SimulationDriver strategy);
}
