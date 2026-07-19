package com.tsegabrehan.einvoicing.config;

import com.tsegabrehan.einvoicing.service.HttpPeppolAccessPointClient;
import com.tsegabrehan.einvoicing.service.PeppolAccessPointClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@Configuration
public class PeppolConfig {

    private static final Logger log = LoggerFactory.getLogger(PeppolConfig.class);

    /**
     * If {@code einvoicing.peppol.base-url} is set, wires a real
     * {@link HttpPeppolAccessPointClient} pointed at it (see that class's
     * javadoc for exactly what "real" means here — a genuine HTTP client,
     * verified against a local test server, but not exercised against the
     * actual Peppol network from this sandbox).
     * <p>
     * Otherwise falls back to a stub that always throws
     * {@link UnsupportedOperationException}, so the rest of the
     * application still has a complete, exercisable path end-to-end
     * without silently pretending transmission succeeded.
     */
    @Bean
    public PeppolAccessPointClient peppolAccessPointClient(
            @Value("${einvoicing.peppol.base-url:}") String baseUrl,
            @Value("${einvoicing.peppol.api-key:}") String apiKey,
            @Value("${einvoicing.peppol.timeout-seconds:10}") long timeoutSeconds) {

        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("No einvoicing.peppol.base-url configured; Peppol transmission will fail with "
                    + "UnsupportedOperationException until a real Access Point endpoint is configured.");
            return (participantId, document, contentType) -> {
                throw new UnsupportedOperationException(
                        "No Peppol Access Point is configured. Set einvoicing.peppol.base-url "
                                + "(and einvoicing.peppol.api-key if required) to enable Peppol transmission.");
            };
        }
        return new HttpPeppolAccessPointClient(baseUrl, apiKey, Duration.ofSeconds(timeoutSeconds));
    }
}

