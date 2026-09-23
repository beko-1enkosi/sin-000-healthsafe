package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.EquipmentFailureConsumer;
import io.javalin.Javalin;

public class EquipmentAlertServiceApp {

    public static void main(String[] args) {
        EquipmentFailureConsumer consumer = new EquipmentFailureConsumer();
        consumer.start();

        Javalin app = Javalin.create().start(7034);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/alerts/latest", ctx -> {
            EquipmentFailureConsumer.EquipmentAlert alert = consumer.getLatestAlert();

            if (alert == null) {
                ctx.status(404).json(new ErrorResponse("No equipment alert received yet"));
                return;
            }

            ctx.json(alert);
        });
    }

    public record ErrorResponse(String error) {}
}