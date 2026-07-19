package com.tsegabrehan.einvoicing.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The data an upstream system (e.g. the Financial &amp; Inventory Management
 * Platform's {@code SalesOrder}/{@code Invoice} records) supplies to this
 * service to generate a compliant e-invoice (SRS 2.1, FR-1).
 * <p>
 * This is intentionally a plain input model, not a JPA entity: the system
 * of record for the underlying business transaction lives upstream. What
 * this service persists is the generated {@link EInvoice} document and its
 * compliance metadata.
 *
 * @param invoiceNumber   unique invoice number assigned by the issuer
 * @param issueDate       invoice issue date
 * @param currencyCode    ISO 4217 currency code, e.g. "EUR"
 * @param seller          the issuing party
 * @param buyer           the recipient party
 * @param lines           invoice line items; must contain at least one line (FR-5)
 * @param paymentMeansText free-text payment terms/means (EN 16931 requires a
 *                          payment means code in a full implementation; kept
 *                          as text here for portfolio-scope simplicity)
 */
public record SourceInvoice(
        String invoiceNumber,
        LocalDate issueDate,
        String currencyCode,
        Party seller,
        Party buyer,
        List<SourceInvoiceLine> lines,
        String paymentMeansText
) {
    /** Sum of all line net amounts (EN 16931 BR-CO-10). */
    public BigDecimal sumLineNetAmounts() {
        if (lines == null) {
            return BigDecimal.ZERO;
        }
        return lines.stream()
                .map(SourceInvoiceLine::netAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
