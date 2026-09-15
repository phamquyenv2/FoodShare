package com.datn.foodshare.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfobipOtpClientTest {

    private HttpServer server;
    private InfobipOtpClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/2fa/2/pin", this::handleRequest);
        server.start();

        client = new InfobipOtpClient(
                "http://localhost:" + server.getAddress().getPort(),
                "test-api-key",
                "application-id",
                "message-id",
                "FoodShare",
                2_000);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void deserializesSendAndVerifyResponsesWithSpringJacksonConverter() {
        assertEquals("pin-id", client.sendPin("84912345678"));
        assertTrue(client.verifyPin("pin-id", "1234"));
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        String response = exchange.getRequestURI().getPath().endsWith("/verify")
                ? "{\"verified\":true}"
                : "{\"pinId\":\"pin-id\"}";
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
