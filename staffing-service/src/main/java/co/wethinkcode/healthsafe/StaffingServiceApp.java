package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.StaffingEventPublisher;
import io.javalin.Javalin;

public class StaffingServiceApp {

    public static void main(String[] args) {
        HospitalClient hospitalClient = new HospitalClient();
        StaffingEventPublisher publisher = new StaffingEventPublisher();

        Javalin app = Javalin.create().start(7033);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/staffing/{wardId}", ctx -> {
            String wardId = ctx.pathParam("wardId").trim().toUpperCase();

            try {
                HospitalClient.Ward ward = hospitalClient.getWard(wardId);

                if (ward == null) {
                    ctx.status(404).json(new ErrorResponse("Ward not found: " + wardId));
                    return;
                }

                int alertLevel = hospitalClient.getAlertLevel();
                int doctorsRequired = calculateDoctors(alertLevel);

                String event = ward.wardId() + "," + ward.department() + "," + alertLevel + "," + doctorsRequired;
                publisher.publish(event);

                ctx.json(new StaffingResponse(
                        ward.wardId(),
                        ward.department(),
                        alertLevel,
                        doctorsRequired
                ));

            } catch (HospitalClient.DownstreamException e) {
                ctx.status(503).json(new ErrorResponse(e.getMessage()));
            }
        });
    }

    private static int calculateDoctors(int alertLevel) {
        if (alertLevel <= 2) return 1;
        if (alertLevel <= 5) return 2;
        return 3;
    }

    public record StaffingResponse(String wardId, String department, int alertLevel, int doctorsRequired) {}
    public record ErrorResponse(String error) {}
}