package com.tsegabrehan.einvoicing.api.dto;

import com.tsegabrehan.einvoicing.domain.*;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class InvoiceMapper {

    public static SourceInvoice toDomain(GenerateInvoiceRequest req) {
        Party seller = new Party(req.seller.name, req.seller.vatId, req.seller.addressLine,
                req.seller.postalCode, req.seller.city, req.seller.countryCode);
        Party buyer = new Party(req.buyer.name, req.buyer.vatId, req.buyer.addressLine,
                req.buyer.postalCode, req.buyer.city, req.buyer.countryCode);
        List<SourceInvoiceLine> lines = req.lines.stream()
                .map(l -> new SourceInvoiceLine(l.lineId, l.description, l.quantity, l.unitPrice,
                        l.vatCategoryCode, l.vatRatePercent))
                .collect(Collectors.toList());
        return new SourceInvoice(req.invoiceNumber, req.issueDate, req.currencyCode, seller, buyer, lines, req.paymentMeansText);
    }

    public record EInvoiceResponse(
            String id,
            String sourceInvoiceNumber,
            InvoiceFormat format,
            InvoiceDirection direction,
            InvoiceStatus status,
            Instant createdAt
    ) {
        public static EInvoiceResponse from(EInvoice e) {
            return new EInvoiceResponse(e.getId(), e.getSourceInvoiceNumber(), e.getFormat(),
                    e.getDirection(), e.getStatus(), e.getCreatedAt());
        }
    }

    public record ValidationResponse(
            String eInvoiceId,
            String ruleSetVersion,
            ValidationOutcome outcome,
            List<String> details,
            Instant validatedAt
    ) {
        public static ValidationResponse from(ValidationResult r) {
            return new ValidationResponse(r.getEInvoiceId(), r.getRuleSetVersion(), r.getOutcome(),
                    r.getDetails(), r.getValidatedAt());
        }
    }

    public record TransmissionResponse(
            String eInvoiceId,
            TransmissionMethod method,
            String recipient,
            TransmissionStatus status,
            Instant timestamp
    ) {
        public static TransmissionResponse from(TransmissionRecord t) {
            return new TransmissionResponse(t.getEInvoiceId(), t.getMethod(), t.getRecipient(),
                    t.getStatus(), t.getTimestamp());
        }
    }
}
