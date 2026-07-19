package com.tsegabrehan.einvoicing.service;

import org.springframework.stereotype.Component;

/**
 * Integration seam for a Peppol Access Point (FR-11).
 * <p>
 * A real implementation of this interface would talk AS4 to either a
 * self-hosted Access Point or a certified third-party provider's API,
 * addressed by the recipient's Peppol participant ID. Getting onto the
 * Peppol network requires becoming (or contracting) a certified Access
 * Point operator — that's an organizational/legal step, not something
 * that can be stood up inside a portfolio project.
 * <p>
 * {@code com.tsegabrehan.einvoicing.config.PeppolConfig} supplies a stub
 * bean by default so the rest of the application (service layer, controller,
 * transmission records) has a complete, exercisable path end-to-end, while
 * being explicit that no message is actually delivered anywhere until a
 * real implementation is wired in.
 */
public interface PeppolAccessPointClient {

    /**
     * @throws UnsupportedOperationException always, in the stub implementation
     */
    void send(String peppolParticipantId, byte[] document, String contentType);
}

