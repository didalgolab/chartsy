/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation.services;

import one.chartsy.core.AbstractSimpleLauncher;
import one.chartsy.core.LaunchConfiguration;
import one.chartsy.trade.strategy.Strategy;

public class SimulationLauncher extends AbstractSimpleLauncher {

    public SimulationLauncher() {
        super(Strategy.class);
    }

    @Override
    public <R> R launch(LaunchConfiguration<R> configuration) {
        return null;
    }
}
