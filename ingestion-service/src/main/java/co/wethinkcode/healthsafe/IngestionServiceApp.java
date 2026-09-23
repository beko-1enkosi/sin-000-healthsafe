package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.util.List;

public class IngestionServiceApp {

    public static void main(String[] args) {

        WardDataLoader loader = new WardDataLoader();
        List<Ward> wards = loader.loadWards();

        ObjectMapper objectMapper = new ObjectMapper();

        Javalin app = Javalin.create().start(7030);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/wards", ctx -> {
            ctx.contentType("application/json");
            ctx.result(objectMapper.writeValueAsString(wards));
        });

        System.out.println("Loaded " + wards.size() + " cleaned wards.");
    }
}