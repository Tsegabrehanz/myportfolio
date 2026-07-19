package com.tsegabrehan.einvoicing.api.dto;

import com.tsegabrehan.einvoicing.domain.TransmissionMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request payload for {@code POST /api/v1/einvoices/{id}/send} (FR-10, FR-11). */
public class SendInvoiceRequest {

    @NotNull
    public TransmissionMethod method;

    /** Email address if method=EMAIL, Peppol participant ID if method=PEPPOL. */
    @NotBlank
    public String recipient;
}
