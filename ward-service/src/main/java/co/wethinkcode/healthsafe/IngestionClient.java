package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class IngestionClient {

    private static final String INGESTION_URL =
            "http://localhost:7030/wards";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public IngestionClient() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public List<Ward> getWards() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(INGESTION_URL))
                .GET()
                .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Ingestion service returned status " + response.statusCode());
            }

            return objectMapper.readValue(
                    response.body(),
                    new TypeReference<List<Ward>>() {
                    }
            );

        } catch (IOException e) {
            throw new RuntimeException("Could not communicate with ingestion service", e);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new RuntimeException("Request to ingestion service was interrupted", e);
        }
    }
}