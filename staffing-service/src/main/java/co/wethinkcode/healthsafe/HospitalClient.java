package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HospitalClient {

    private static final String WARD_URL = "http://localhost:7031/wards/";
    private static final String ALERT_URL = "http://localhost:7032/alert-level";

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public Ward getWard(String wardId) {
        HttpResponse<String> response = get(WARD_URL + wardId);

        if (response.statusCode() == 404) return null;
        if (response.statusCode() != 200) throw new DownstreamException("Ward service returned " + response.statusCode());

        try {
            return mapper.readValue(response.body(), Ward.class);
        } catch (IOException e) {
            throw new DownstreamException("Invalid response from ward service");
        }
    }

    public int getAlertLevel() {
        HttpResponse<String> response = get(ALERT_URL);

        if (response.statusCode() != 200) throw new DownstreamException("Alert service returned " + response.statusCode());

        try {
            return mapper.readValue(response.body(), AlertLevel.class).level();
        } catch (IOException e) {
            throw new DownstreamException("Invalid response from alert service");
        }
    }

    private HttpResponse<String> get(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();

        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new DownstreamException("Could not reach downstream service");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DownstreamException("Request interrupted");
        }
    }

    public record Ward(String wardId, String wing, String department, Integer bedsAvailable) {}
    public record AlertLevel(int level) {}

    public static class DownstreamException extends RuntimeException {
        public DownstreamException(String message) {
            super(message);
        }
    }
}