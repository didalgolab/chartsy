/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.simulation.services;

import one.chartsy.simulation.SimulationResult;

import java.util.Map;

@FunctionalInterface
public interface SimulationResultBuilderFactory {

    SimulationResult.Builder create(Map<String,?> props);
}
