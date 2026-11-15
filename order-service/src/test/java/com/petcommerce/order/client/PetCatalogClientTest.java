package com.petcommerce.order.client;

import com.petcommerce.order.domain.exception.PetCatalogUnavailableException;
import com.petcommerce.order.domain.exception.PetNotFoundException;
import com.petcommerce.order.domain.exception.PetUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** R-SVC-04: catalog answers and failures are mapped, and a slow catalog times out. Uses a tiny local HTTP server. */
class PetCatalogClientTest {

    private HttpServer server;
    private volatile int status;
    private volatile String body;
    private volatile long delayMs;
    private PetCatalogClient client;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/pets/", exchange -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        client = new PetCatalogClient("http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(1), Duration.ofMillis(300));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void respond(int code, String json) {
        status = code;
        body = json;
    }

    @Test
    void availablePetIsReturned() {
        UUID id = UUID.randomUUID();
        respond(200, "{\"id\":\"" + id + "\",\"name\":\"Rex\",\"price\":10.5,\"availabilityStatus\":\"AVAILABLE\"}");
        assertEquals("Rex", client.getPetById(id).name());
    }

    @Test
    void soldPetIsUnavailable() {
        respond(200, "{\"name\":\"Rex\",\"availabilityStatus\":\"SOLD\"}");
        assertThrows(PetUnavailableException.class, () -> client.getPetById(UUID.randomUUID()));
    }

    @Test
    void notFoundIsMapped() {
        respond(404, "{\"status\":404}");
        assertThrows(PetNotFoundException.class, () -> client.getPetById(UUID.randomUUID()));
    }

    @Test
    void serverErrorAndOtherClientErrorsMeanCatalogUnavailable() {
        respond(500, "{\"status\":500}");
        assertThrows(PetCatalogUnavailableException.class, () -> client.getPetById(UUID.randomUUID()));
        respond(400, "{\"status\":400}");
        assertThrows(PetCatalogUnavailableException.class, () -> client.getPetById(UUID.randomUUID()));
    }

    @Test
    void slowCatalogTimesOut() {
        respond(200, "{\"availabilityStatus\":\"AVAILABLE\"}");
        delayMs = 1500;
        assertThrows(PetCatalogUnavailableException.class, () -> client.getPetById(UUID.randomUUID()));
    }

    @Test
    void unreachableCatalogIsUnavailable() {
        server.stop(0);
        assertThrows(PetCatalogUnavailableException.class, () -> client.getPetById(UUID.randomUUID()));
    }
}
