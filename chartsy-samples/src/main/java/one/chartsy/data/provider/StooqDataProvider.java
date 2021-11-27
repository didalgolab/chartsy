package one.chartsy.data.provider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

public class StooqDataProvider {


    public static void main(String[] args) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("https://stooq.pl/cmp/?q=kop"))
                .version(HttpClient.Version.HTTP_2)
                .timeout(Duration.of(10, ChronoUnit.SECONDS))
                .GET()
                .build();

        var client = HttpClient.newHttpClient();
        var reponse = client.send(request, HttpResponse.BodyHandlers.ofString());
        var text = reponse.body();

        final String TRIM_LEFT = "('", TRIM_RIGHT = "')";
        int trimFrom = text.indexOf(TRIM_LEFT), trimTo = text.lastIndexOf(TRIM_RIGHT);
        text = text.substring(trimFrom + TRIM_LEFT.length(), trimTo);
        var symbolLines = text.split("\\|");
        System.out.println(Arrays.asList(symbolLines));

    }
}
