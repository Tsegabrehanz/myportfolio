package com.tsegabrehan.einvoicing.domain;

import java.math.BigDecimal;

/**
 * A single invoice line. Monetary fields use {@link BigDecimal} throughout
 * (NFR-7: no floating-point arithmetic for money/tax).
 *
 * @param lineId      caller-assigned line identifier (e.g. "1", "2", ...)
 * @param description free-text description of the goods/service
 * @param quantity     quantity invoiced
 * @param unitPrice    net unit price, currency as per the parent invoice
 * @param vatCategoryCode EN 16931 VAT category code, e.g. "S" (standard rate),
 *                        "Z" (zero rated), "E" (exempt), "AE" (reverse charge)
 * @param vatRatePercent  VAT rate as a percentage, e.g. 19.00 for the German
 *                        standard rate; must be zero/absent for exempt categories
 */
public record SourceInvoiceLine(
        String lineId,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        String vatCategoryCode,
        BigDecimal vatRatePercent
) {
    /** Net line amount = quantity * unitPrice, per EN 16931 BR-CO-10 semantics. */
    public BigDecimal netAmount() {
        if (quantity == null || unitPrice == null) {
            return null;
        }
        return quantity.multiply(unitPrice);
    }
}
