package com.tsegabrehan.einvoicing.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link HttpPeppolAccessPointClient} against a genuine local
 * HTTP server ({@code com.sun.net.httpserver} — JDK-native, no test-only
 * dependency needed) rather than a mocked HTTP layer. This is the same
 * approach used to verify the client during development in a sandbox
 * without Maven Central access — see the class's own javadoc and the
 * project README for what this does and doesn't prove about real Peppol
 * network connectivity.
 */
class HttpPeppolAccessPointClientTest {

    @Test
    void sendsAWellFormedPostRequest() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<byte[]> body = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().toString());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(readAll(exchange.getRequestBody()));
            byte[] resp = "{\"status\":\"accepted\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(202, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();
        try {
            HttpPeppolAccessPointClient client = new HttpPeppolAccessPointClient(
                    "http://localhost:" + server.getAddress().getPort(), null, Duration.ofSeconds(5));
            byte[] doc = "<Invoice>test</Invoice>".getBytes(StandardCharsets.UTF_8);

            client.send("0088:1234567891234", doc, "application/xml");

            assertEquals("POST", method.get());
            assertTrue(path.get().contains("/participants/"));
            assertEquals("application/xml", contentType.get());
            assertArrayEquals(doc, body.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void nonSuccessStatusSurfacesAsException() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            byte[] resp = "{\"error\":\"invalid participant\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();
        try {
            HttpPeppolAccessPointClient client = new HttpPeppolAccessPointClient(
                    "http://localhost:" + server.getAddress().getPort(), null, Duration.ofSeconds(5));

            var ex = assertThrows(HttpPeppolAccessPointClient.PeppolTransmissionException.class,
                    () -> client.send("0088:bad", "<x/>".getBytes(StandardCharsets.UTF_8), "application/xml"));
            assertTrue(ex.getMessage().contains("400"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void apiKeyIsSentAsBearerToken() throws Exception {
        AtomicReference<String> auth = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });
        server.start();
        try {
            HttpPeppolAccessPointClient client = new HttpPeppolAccessPointClient(
                    "http://localhost:" + server.getAddress().getPort(), "secret-key-123", Duration.ofSeconds(5));
            client.send("0088:1234567891234", "<x/>".getBytes(StandardCharsets.UTF_8), "application/xml");

            assertEquals("Bearer secret-key-123", auth.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void blankParticipantIdRejectedBeforeNetworkCall() {
        HttpPeppolAccessPointClient client = new HttpPeppolAccessPointClient(
                "http://localhost:1", null, Duration.ofSeconds(1));
        assertThrows(IllegalArgumentException.class,
                () -> client.send("", "<x/>".getBytes(StandardCharsets.UTF_8), "application/xml"));
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        in.transferTo(out);
        return out.toByteArray();
    }
}
