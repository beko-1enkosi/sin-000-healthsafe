package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.util.concurrent.atomic.AtomicInteger;

public class AlertLevelServiceApp {

    public static void main(String[] args) {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicInteger alertLevel = new AtomicInteger(0);

        Javalin app = Javalin.create().start(7032);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/alert-level", ctx ->
                ctx.json(new AlertLevelResponse(alertLevel.get())));

        app.put("/alert-level", ctx -> {
            try {
                AlertLevelRequest request =
                        objectMapper.readValue(ctx.body(), AlertLevelRequest.class);

                if (request.level() == null || request.level() < 0 || request.level() > 8) {
                    ctx.status(400).json(new ErrorResponse("Alert level must be between 0 and 8"));
                    return;
                }

                alertLevel.set(request.level());
                ctx.json(new AlertLevelResponse(alertLevel.get()));

            } catch (JsonProcessingException e) {
                ctx.status(400).json(new ErrorResponse("Invalid JSON request"));
            }
        });
    }

    public record AlertLevelRequest(Integer level) {}
    public record AlertLevelResponse(int level) {}
    public record ErrorResponse(String error) {}
}