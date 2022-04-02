package one.chartsy.frontend.rest.controllers;

import one.chartsy.kernel.Kernel;
import one.chartsy.simulation.ImmutableSimulationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

//@RestController
//@RequestMapping("/backtest")
public class BacktestController {

    //@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    //@Autowired
    private Kernel kernel;

    //@PostMapping("/result")
    public String addBacktestResult(@RequestBody ImmutableSimulationResult result) {
        kernel.publishEvent(result);
        return "{\"Hello\":\"World\",\"cnt\"}";
    }
}
