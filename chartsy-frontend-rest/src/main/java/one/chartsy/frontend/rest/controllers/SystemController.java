package one.chartsy.frontend.rest.controllers;

import one.chartsy.Symbol;
import one.chartsy.kernel.GlobalSymbolSelection;
import org.openide.util.Lookup;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v0/chartsy")
public class SystemController {

    @GetMapping("/status")
    public Mono<SystemStatusData> getStatus() {
        return Mono.just(new SystemStatusData());
    }

    @GetMapping("/selected-symbols")
    public Flux<Symbol> getSelectedSymbols() {
        var selection = Lookup.getDefault().lookup(GlobalSymbolSelection.class);
        return Flux.fromIterable(selection.getSelectedSymbols());
    }

    public static class SystemStatusData {
        private String status = "OK";

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
