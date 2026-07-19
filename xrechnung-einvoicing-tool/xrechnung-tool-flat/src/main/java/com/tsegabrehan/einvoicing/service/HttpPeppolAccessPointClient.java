package com.tsegabrehan.einvoicing.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * A real, working {@link PeppolAccessPointClient} implementation using only
 * {@code java.net.http} (JDK-native, no external dependency needed).
 * <p>
 * <b>What "real" means here, precisely:</b> this performs an actual HTTP
 * POST — with proper timeouts, status-code handling, and error surfacing —
 * to a configured Access Point base URL. Structurally, it matches how most
 * commercial Peppol Access Point providers expose their gateway: a REST/
 * HTTP API that internally handles the AS4 messaging protocol and SMP
 * (Service Metadata Publisher) lookups on your behalf, rather than
 * requiring the client application to speak AS4 itself.
 * <p>
 * <b>What it is not:</b> a self-hosted AS4 endpoint, and it cannot be
 * exercised against the real Peppol network from this environment, because
 * that needs a certified Access Point account and credentials that don't
 * exist in a sandbox. What <em>is</em> verified — see
 * {@code HttpPeppolAccessPointClientTest} — is that the client sends a
 * correctly-formed request and handles success/error responses correctly,
 * using a local mock HTTP server (JDK-native {@code com.sun.net.httpserver},
 * no external dependency there either). That test is the honest substitute
 * for "hitting the real network" available in this environment; point
 * {@code einvoicing.peppol.base-url} at a real provider's endpoint (with
 * real credentials) to use this against the actual Peppol network.
 * <p>
 * Only activated when {@code einvoicing.peppol.base-url} is configured —
 * see {@code PeppolConfig}, which otherwise falls back to the documented
 * stub.
 */
public class HttpPeppolAccessPointClient implements PeppolAccessPointClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final Duration timeout;

    public HttpPeppolAccessPointClient(String baseUrl, String apiKey, Duration timeout) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    @Override
    public void send(String peppolParticipantId, byte[] document, String contentType) {
        if (peppolParticipantId == null || peppolParticipantId.isBlank()) {
            throw new IllegalArgumentException("Peppol participant ID is required.");
        }
        String encodedParticipantId = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(peppolParticipantId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        URI uri = URI.create(baseUrl + "/participants/" + encodedParticipantId + "/documents");

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(timeout)
                .header("Content-Type", contentType != null ? contentType : "application/xml")
                .POST(HttpRequest.BodyPublishers.ofByteArray(document));

        if (apiKey != null && !apiKey.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        try {
            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PeppolTransmissionException(
                        "Peppol Access Point responded with HTTP " + response.statusCode()
                                + " for participant " + peppolParticipantId + ": " + truncate(response.body()));
            }
        } catch (java.io.IOException e) {
            throw new PeppolTransmissionException(
                    "Network error sending to Peppol Access Point for participant " + peppolParticipantId, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PeppolTransmissionException(
                    "Interrupted while sending to Peppol Access Point for participant " + peppolParticipantId, e);
        }
    }

    private String truncate(String s) {
        if (s == null) return "";
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }

    public static class PeppolTransmissionException extends RuntimeException {
        public PeppolTransmissionException(String message) {
            super(message);
        }
        public PeppolTransmissionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
