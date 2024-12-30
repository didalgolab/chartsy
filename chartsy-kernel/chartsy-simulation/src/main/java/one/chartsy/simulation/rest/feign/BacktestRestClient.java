/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.simulation.rest.feign;

import feign.Headers;
import feign.RequestLine;
import one.chartsy.simulation.SimulationResult;

public interface BacktestRestClient {

    @RequestLine("POST /backtest/result")
    @Headers("Content-Type: application/json")
    void updateSimulationResult(SimulationResult result);
}
