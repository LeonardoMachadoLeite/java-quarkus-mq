package com.example.ratelimit;

import com.example.ratelimit.domain.Priority;
import com.example.ratelimit.domain.SubmitJobRequest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.awaitility.Awaitility.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JobResourceIT {

    static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(options().port(9999));
        wireMock.start();
        WireMock.configureFor("localhost", 9999);
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @Test
    @Order(1)
    void submitJob_returns202WithJobId() {
        stubFor(get(urlPathMatching("/.*")).willReturn(aResponse().withStatus(200).withBody("{}")));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "provider", "github",
                        "method", "GET",
                        "targetUrl", "http://localhost:9999/api/test",
                        "priority", "NORMAL"
                ))
                .when().post("/api/jobs")
                .then()
                .statusCode(202)
                .body("jobId", notNullValue())
                .body("estimatedWaitSeconds", greaterThanOrEqualTo(0));
    }

    @Test
    @Order(2)
    void getJob_returns404ForUnknownId() {
        given()
                .when().get("/api/jobs/" + UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    @Order(3)
    void submitJob_unknownProvider_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "provider", "unknown-provider",
                        "method", "GET",
                        "targetUrl", "http://localhost:9999/test"
                ))
                .when().post("/api/jobs")
                .then()
                .statusCode(400)
                .body("error", containsString("Unknown provider"));
    }

    @Test
    @Order(4)
    void listProviders_returnsConfiguredProviders() {
        given()
                .when().get("/api/providers")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0));
    }

    @Test
    @Order(5)
    void getProviderStats_returnsStatsForKnownProvider() {
        given()
                .when().get("/api/providers/github/stats")
                .then()
                .statusCode(200)
                .body("provider", equalTo("github"))
                .body("queueDepth", greaterThanOrEqualTo(0));
    }

    @Test
    @Order(6)
    void getProviderStats_returns404ForUnknownProvider() {
        given()
                .when().get("/api/providers/unknown/stats")
                .then()
                .statusCode(404);
    }

    @Test
    @Order(7)
    void cancelJob_nonExistentJob_returns404() {
        given()
                .when().delete("/api/jobs/" + UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    @Order(8)
    void healthEndpoint_isUp() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }
}
