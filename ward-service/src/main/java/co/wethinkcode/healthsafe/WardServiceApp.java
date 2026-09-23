package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.util.List;

public class WardServiceApp {

    public static void main(String[] args) {

        IngestionClient ingestionClient = new IngestionClient();
        ObjectMapper objectMapper = new ObjectMapper();

        Javalin app = Javalin.create().start(7031);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/wards", ctx -> {
            try {
                List<Ward> wards = ingestionClient.getWards();

                ctx.contentType("application/json");
                ctx.result(objectMapper.writeValueAsString(wards));

            } catch (RuntimeException e) {
                ctx.status(503);
                ctx.json(new ErrorResponse("Ingestion service unavailable"));
            }
        });

        app.get("/wards/{id}", ctx -> {
            try {
                String requestedId = ctx.pathParam("id").trim().toUpperCase();

                List<Ward> wards = ingestionClient.getWards();

                Ward ward = null;

                for (Ward currentWard : wards) {
                    if (currentWard.wardId().equalsIgnoreCase(requestedId)) {
                        ward = currentWard;
                        break;
                    }
                }

                if (ward == null) {
                    ctx.status(404);
                    ctx.json(new ErrorResponse("Ward not found: " + requestedId));
                    return;
                }

                ctx.json(ward);

            } catch (RuntimeException e) {
                ctx.status(503);
                ctx.json(new ErrorResponse("Ingestion service unavailable"));
            }
        });

        app.get("/departments", ctx -> {
            try {
                List<String> departments =
                        ingestionClient.getWards()
                                .stream()
                                .map(Ward::department)
                                .filter(department -> department != null)
                                .distinct()
                                .sorted()
                                .toList();

                ctx.json(departments);

            } catch (RuntimeException e) {
                ctx.status(503);
                ctx.json(new ErrorResponse("Ingestion service unavailable"));
            }
        });
    }

    public record ErrorResponse(String error) {
    }
}