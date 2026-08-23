package com.mthind.tentflow.operations;

import com.mthind.tentflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

//starts real HTTP servers and proves the public/internal operations boundary
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0"
)
class OperationalEndpointsIntegrationTest extends PostgresIntegrationTest {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int applicationPort;

    @LocalManagementPort
    private int managementPort;

    @Test
    void publicProbesReflectApplicationAndDatabaseReadiness()
            throws IOException, InterruptedException {
        HttpResponse<String> liveness = get(applicationPort, "/livez");
        HttpResponse<String> readiness = get(applicationPort, "/readyz");

        assertEquals(200, liveness.statusCode());
        assertTrue(liveness.body().contains("\"status\":\"UP\""));

        assertEquals(200, readiness.statusCode());
        assertTrue(readiness.body().contains("\"status\":\"UP\""));

        //the public probe response deliberately does not expose component, database, or connection-pool details
        assertFalse(readiness.body().contains("db"));
        assertFalse(readiness.body().contains("jdbc"));
    }

    @Test
    void managementPortExposesOnlyHealthAndPrometheus()
            throws IOException, InterruptedException {
        HttpResponse<String> health = get(
                managementPort,
                "/actuator/health/readiness"
        );

        HttpResponse<String> prometheus = get(
                managementPort,
                "/actuator/prometheus"
        );

        HttpResponse<String> environment = get(
                managementPort,
                "/actuator/env"
        );

        HttpResponse<String> publicActuator = get(
                applicationPort,
                "/actuator/prometheus"
        );

        assertEquals(200, health.statusCode());
        assertTrue(health.body().contains("\"status\":\"UP\""));

        assertEquals(200, prometheus.statusCode());
        assertTrue(prometheus.body().contains("jvm_info"));

        //unexposed endpoints can fall through to the browser chain redirect, or resolve as 403/404. They must never return endpoint data from either port
        assertNotEquals(200, environment.statusCode());
        assertFalse(environment.body().contains("activeProfiles"));
        assertNotEquals(200, publicActuator.statusCode());
        assertFalse(publicActuator.body().contains("jvm_info"));
    }

    private HttpResponse<String> get(int port, String path)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }
}