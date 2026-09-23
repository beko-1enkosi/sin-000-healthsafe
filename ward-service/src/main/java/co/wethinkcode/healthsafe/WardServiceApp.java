package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.EquipmentFailurePublisher;
import co.wethinkcode.healthsafe.mq.StaffingEventSubscriber;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.util.List;

public class WardServiceApp {

    public static void main(String[] args) {
        IngestionClient ingestionClient = new IngestionClient();
        ObjectMapper objectMapper = new ObjectMapper();

        StaffingEventSubscriber subscriber = new StaffingEventSubscriber();
        subscriber.start();

        EquipmentFailurePublisher equipmentPublisher = new EquipmentFailurePublisher();

        Javalin app = Javalin.create().start(7031);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/wards", ctx -> {
            try {
                ctx.json(ingestionClient.getWards());
            } catch (RuntimeException e) {
                ctx.status(503).json(new ErrorResponse("Ingestion service unavailable"));
            }
        });

        app.get("/wards/{id}", ctx -> {
            try {
                String requestedId = ctx.pathParam("id").trim().toUpperCase();
                Ward ward = findWard(ingestionClient.getWards(), requestedId);

                if (ward == null) {
                    ctx.status(404).json(new ErrorResponse("Ward not found: " + requestedId));
                    return;
                }

                ctx.json(ward);
            } catch (RuntimeException e) {
                ctx.status(503).json(new ErrorResponse("Ingestion service unavailable"));
            }
        });

        app.get("/departments", ctx -> {
            try {
                List<String> departments = ingestionClient.getWards()
                        .stream()
                        .map(Ward::department)
                        .filter(department -> department != null)
                        .distinct()
                        .sorted()
                        .toList();

                ctx.json(departments);
            } catch (RuntimeException e) {
                ctx.status(503).json(new ErrorResponse("Ingestion service unavailable"));
            }
        });

        app.get("/staffing-events/latest", ctx -> {
            String event = subscriber.getLatestEvent();

            if (event == null) {
                ctx.status(404).json(new ErrorResponse("No staffing event received yet"));
                return;
            }

            ctx.json(new StaffingEventResponse(event));
        });

        app.post("/wards/{id}/equipment-failures", ctx -> {
            try {
                String wardId = ctx.pathParam("id").trim().toUpperCase();
                Ward ward = findWard(ingestionClient.getWards(), wardId);

                if (ward == null) {
                    ctx.status(404).json(new ErrorResponse("Ward not found: " + wardId));
                    return;
                }

                EquipmentFailureRequest request =
                        objectMapper.readValue(ctx.body(), EquipmentFailureRequest.class);

                if (request.equipment() == null || request.equipment().isBlank()) {
                    ctx.status(400).json(new ErrorResponse("Equipment name is required"));
                    return;
                }

                EquipmentFailureEvent event = new EquipmentFailureEvent(
                        wardId,
                        ward.department(),
                        request.equipment(),
                        request.description()
                );

                equipmentPublisher.publish(objectMapper.writeValueAsString(event));

                ctx.status(202).json(new QueueResponse("queued", wardId, request.equipment()));

            } catch (RuntimeException e) {
                ctx.status(503).json(new ErrorResponse("Could not queue equipment failure"));
            } catch (Exception e) {
                ctx.status(400).json(new ErrorResponse("Invalid request"));
            }
        });
    }

    private static Ward findWard(List<Ward> wards, String wardId) {
        for (Ward ward : wards) {
            if (ward.wardId().equalsIgnoreCase(wardId)) return ward;
        }
        return null;
    }

    public record ErrorResponse(String error) {}
    public record StaffingEventResponse(String event) {}
    public record EquipmentFailureRequest(String equipment, String description) {}
    public record EquipmentFailureEvent(String wardId, String department, String equipment, String description) {}
    public record QueueResponse(String status, String wardId, String equipment) {}
}