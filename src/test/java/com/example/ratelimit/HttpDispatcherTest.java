package com.example.ratelimit;

import com.example.ratelimit.dispatcher.DispatchResult;
import com.example.ratelimit.dispatcher.HttpDispatcher;
import com.example.ratelimit.domain.ApiRequest;
import com.example.ratelimit.domain.Priority;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class HttpDispatcherTest {

    static WireMockServer wireMock;

    @Inject
    HttpDispatcher dispatcher;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(options().port(9998));
        wireMock.start();
        WireMock.configureFor("localhost", 9998);
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void reset() {
        wireMock.resetAll();
    }

    private ApiRequest request(String method, String path) {
        return new ApiRequest(
                UUID.randomUUID(), "github", method,
                "http://localhost:9998" + path,
                Map.of(), null, Priority.NORMAL, null, null
        );
    }

    @Test
    void dispatch_get_200_returnsSuccess() {
        stubFor(get(urlEqualTo("/test")).willReturn(
                aResponse().withStatus(200).withBody("{\"ok\":true}")));

        DispatchResult result = dispatcher.dispatch(request("GET", "/test"));
        assertInstanceOf(DispatchResult.Success.class, result);
        assertEquals(200, ((DispatchResult.Success) result).response().statusCode());
    }

    @Test
    void dispatch_post_201_returnsSuccess() {
        stubFor(post(urlEqualTo("/resource")).willReturn(
                aResponse().withStatus(201).withBody("{\"id\":1}")));

        DispatchResult result = dispatcher.dispatch(new ApiRequest(
                UUID.randomUUID(), "github", "POST",
                "http://localhost:9998/resource",
                Map.of(), "{\"name\":\"test\"}", Priority.NORMAL, null, null
        ));
        assertInstanceOf(DispatchResult.Success.class, result);
    }

    @Test
    void dispatch_429_returnsRateLimited() {
        stubFor(get(urlEqualTo("/limited")).willReturn(
                aResponse().withStatus(429).withHeader("Retry-After", "60")));

        DispatchResult result = dispatcher.dispatch(request("GET", "/limited"));
        assertInstanceOf(DispatchResult.RateLimited.class, result);
        var rateLimited = (DispatchResult.RateLimited) result;
        assertTrue(rateLimited.retryAfter().isPresent());
        assertEquals(60, rateLimited.retryAfter().get().toSeconds());
    }

    @Test
    void dispatch_500_returnsError() {
        stubFor(get(urlEqualTo("/error")).willReturn(
                aResponse().withStatus(500).withBody("Internal error")));

        DispatchResult result = dispatcher.dispatch(request("GET", "/error"));
        assertInstanceOf(DispatchResult.Error.class, result);
        assertEquals(500, ((DispatchResult.Error) result).response().statusCode());
    }
}
