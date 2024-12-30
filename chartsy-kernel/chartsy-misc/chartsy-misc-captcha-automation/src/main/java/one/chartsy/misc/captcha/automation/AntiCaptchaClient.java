package one.chartsy.misc.captcha.automation;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import one.chartsy.kernel.libs.keepass.KeePassDatabase;
import org.openide.util.lookup.ServiceProvider;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Client for solving CAPTCHA images using the AntiCaptcha service.
 * <p>
 * This client interacts with the AntiCaptcha API to convert CAPTCHA images into their textual representations.
 * It handles task creation and result polling until the CAPTCHA is successfully solved or a timeout occurs.
 * </p>
 */
@ServiceProvider(service = CaptchaSolver.class)
public class AntiCaptchaClient implements CaptchaSolver {

    private static final String BASE_URL = "https://api.anti-captcha.com";
    private final Duration timeout = Duration.ofSeconds(60L);
    private final String apiKey;
    private final WebClient webClient;

    public AntiCaptchaClient() {
        this(KeePassDatabase.getDefault()
                .findEntries(AntiCaptchaClient.class.getName()).getFirst().getPassword());
    }

    public AntiCaptchaClient(String apiKey) {
        this(apiKey, WebClient.builder().baseUrl(BASE_URL).build());
    }

    public AntiCaptchaClient(String apiKey, WebClient webClient) {
        this.apiKey = apiKey;
        this.webClient = webClient;
    }

    @Override
    public String imageToText(byte[] imageData) throws IOException, InterruptedException {
        var base64Image = Base64.getEncoder().encodeToString(imageData);
        var createTaskRequest = buildRequest(
                "clientKey", apiKey,
                "task", buildRequest("type", "ImageToTextTask", "body", base64Image)
        );

        var createTaskResponse = callAntiCaptchaApi("/createTask", createTaskRequest);

        var taskIdObj = createTaskResponse.get("taskId");
        if (taskIdObj == null)
            throw new ServiceException("Failed to obtain task ID from response");

        int taskId = ((Number) taskIdObj).intValue();
        return waitTillReady(taskId);
    }

    protected String waitTillReady(int taskId) throws InterruptedException {
        Map<String, Object> taskResultRequest = buildRequest("clientKey", apiKey, "taskId", taskId);
        Instant waitLimit = Instant.now().plus(timeout);

        while (Instant.now().isBefore(waitLimit)) {
            Map<String, Object> taskResultResponse = callAntiCaptchaApi("/getTaskResult", taskResultRequest);

            String status = String.valueOf(taskResultResponse.get("status"));
            if ("ready".equalsIgnoreCase(status)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> solution = (Map<String, Object>) taskResultResponse.getOrDefault("solution", Map.of());
                Object textObj = solution.get("text");
                if (textObj != null)
                    return textObj.toString();

                throw new ServiceException("Solution not found in task result");
            }

            Thread.sleep(1000);
        }

        throw new ServiceException("Captcha not solved within the requested timeout");
    }

    private Map<String, Object> callAntiCaptchaApi(String endpoint, Map<String, Object> requestBody) {
        Map<String, Object> response = this.webClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null)
            throw new ServiceException("API response is null for endpoint: " + endpoint);

        Object errorIdObj = response.get("errorId");
        if (errorIdObj instanceof Number errorId && errorId.intValue() != 0) {
            Object errorDesc = response.getOrDefault("errorDescription", "Unknown error");
            throw new ServiceException(errorDesc.toString());
        }
        return response;
    }

    private static Map<String, Object> buildRequest(Object... keyValues) {
        var map = new HashMap<String, Object>();
        for (int i = 0; i < keyValues.length; i += 2)
            map.put(keyValues[i].toString(), keyValues[i + 1]);

        return map;
    }

    /**
     * Exception thrown when the AntiCaptcha service encounters an error.
     */
    public static class ServiceException extends RuntimeException {
        public ServiceException(String message) {
            super(message);
        }
    }
}
