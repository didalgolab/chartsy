package one.chartsy.rest.controllers;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system")
public class SystemController {

    @PostMapping("/ping")
    public String ping(@RequestBody Data data) {
        return "{\"Hello\":\"World\",\"cnt\":"+data.getText().length()+"}";
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
