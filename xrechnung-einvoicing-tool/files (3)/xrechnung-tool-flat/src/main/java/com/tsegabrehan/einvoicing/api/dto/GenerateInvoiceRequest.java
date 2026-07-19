package com.tsegabrehan.einvoicing.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request payload for {@code POST /api/v1/einvoices} (FR-23).
 * Mirrors {@link com.tsegabrehan.einvoicing.domain.SourceInvoice} but as a
 * bean-validated API shape distinct from the internal domain record, so
 * validation annotations don't leak into core domain code.
 */
public class GenerateInvoiceRequest {

    @NotBlank
    public String invoiceNumber;

    @NotNull
    public LocalDate issueDate;

    @NotBlank
    public String currencyCode;

    @NotNull @Valid
    public PartyDto seller;

    @NotNull @Valid
    public PartyDto buyer;

    @NotEmpty @Valid
    public List<LineDto> lines;

    public String paymentMeansText;

    public static class PartyDto {
        @NotBlank public String name;
        @NotBlank public String vatId;
        public String addressLine;
        public String postalCode;
        public String city;
        @NotBlank public String countryCode;
    }

    public static class LineDto {
        @NotBlank public String lineId;
        @NotBlank public String description;
        @NotNull public BigDecimal quantity;
        @NotNull public BigDecimal unitPrice;
        @NotBlank public String vatCategoryCode;
        public BigDecimal vatRatePercent;
    }
}
