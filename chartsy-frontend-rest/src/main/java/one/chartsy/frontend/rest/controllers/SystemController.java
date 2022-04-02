package one.chartsy.frontend.rest.controllers;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/chartsy")
public class SystemController {

    @GetMapping("/status")
    public Mono<SystemStatusData> getStatus() {
        return Mono.just(new SystemStatusData());
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
