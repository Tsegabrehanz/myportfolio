package com.tsegabrehan.einvoicing.domain;

/**
 * The two EN 16931-conformant formats accepted under the German B2B
 * e-invoicing mandate (SRS 1.3 / FR-1, FR-2).
 */
public enum InvoiceFormat {
    /** Pure XML, UBL or CII syntax, maintained by KoSIT. */
    XRECHNUNG,
    /** Hybrid PDF/A-3 with an embedded EN 16931 XML payload. */
    ZUGFERD
}
