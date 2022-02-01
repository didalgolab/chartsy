package one.chartsy.frontend.rest.controllers;

import one.chartsy.simulation.ImmutableSimulationResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/backtest")
public class BacktestController {

    @PostMapping("/result")
    public String addBacktestResult(@RequestBody ImmutableSimulationResult result) {
        return "{\"Hello\":\"World\",\"cnt\"}";
    }

    public static class Data {
        private String text;

        public Data() {
        }

        public Data(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }
}
