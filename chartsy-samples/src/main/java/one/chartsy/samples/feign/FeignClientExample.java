package one.chartsy.samples.feign;

import feign.Feign;
import feign.Headers;
import feign.RequestLine;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import one.chartsy.rest.controllers.SystemController;

import java.util.concurrent.ThreadLocalRandom;

public class FeignClientExample {

    public static void main(String[] args) {
        SystemService bookClient = Feign.builder()
                //.client(new OkHttpClient())
                .encoder(new JacksonEncoder())
                //.decoder(new JacksonDecoder())
                //.logger(new Slf4jLogger(BookClient.class))
                //.logLevel(Logger.Level.FULL)
                .target(SystemService.class, "http://localhost:8080");

        for (int i = 0; i < 10; i++)
            System.out.println(i + ": " + bookClient.ping(new Data(strOfLen(10_000_000))));
        long startTime = System.nanoTime();
        for (int i = 0; i < 100; i++)
            System.out.println(i + ": " + bookClient.ping(new Data(strOfLen(10_000_000))) + ((double)((System.nanoTime() - startTime)/1000000L))/1000.0);
    }

    public static String strOfLen(int len) {
        StringBuilder buf = new StringBuilder();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < len; i++)
            buf.append((char) r.nextInt(255));

        return buf.toString();
    }

    public static interface SystemService {

        @RequestLine("POST /system/ping")
        @Headers("Content-Type: application/json")
        String ping(Data data);
    }

    public static class Data {
        private String text;

        public Data() {}

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
