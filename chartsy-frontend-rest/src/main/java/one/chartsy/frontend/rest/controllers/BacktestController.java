package one.chartsy.frontend.rest.controllers;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/backtest")
public class BacktestController {

    @GetMapping("/result")
    public String addBacktestResult() {
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
