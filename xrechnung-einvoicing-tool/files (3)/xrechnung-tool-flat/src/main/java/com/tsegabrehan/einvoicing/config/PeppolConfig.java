package com.tsegabrehan.einvoicing.config;

import com.tsegabrehan.einvoicing.service.PeppolAccessPointClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PeppolConfig {

    /**
     * Default bean: a stub that always reports "not configured." Replace
     * with a real Access Point client bean (self-hosted AS4 endpoint or a
     * third-party provider's SDK) once that infrastructure decision is
     * made — see SRS Section 9, Open Questions, and
     * {@link PeppolAccessPointClient}'s javadoc for why this is stubbed
     * rather than faked.
     */
    @Bean
    public PeppolAccessPointClient peppolAccessPointClient() {
        return (participantId, document, contentType) -> {
            throw new UnsupportedOperationException(
                    "No Peppol Access Point is configured. Provide a real "
                            + "PeppolAccessPointClient bean (self-hosted or third-party) to enable Peppol transmission.");
        };
    }
}
