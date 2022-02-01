package one.chartsy.simulation.rest.feign;

import feign.Headers;
import feign.RequestLine;
import one.chartsy.simulation.SimulationResult;

public interface BacktestRestClient {

    @RequestLine("POST /backtest/result")
    @Headers("Content-Type: application/json")
    void updateSimulationResult(SimulationResult result);
}
