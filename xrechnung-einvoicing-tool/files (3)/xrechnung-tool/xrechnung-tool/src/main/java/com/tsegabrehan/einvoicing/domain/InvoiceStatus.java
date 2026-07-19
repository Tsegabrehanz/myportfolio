package com.tsegabrehan.einvoicing.domain;

/**
 * Lifecycle states for an e-invoice (SRS 5. Data Model / EInvoice.status).
 * A document only becomes SENT-eligible after VALIDATED; ARCHIVED is
 * terminal and immutable (FR-18).
 */
public enum InvoiceStatus {
    DRAFT,
    VALIDATED,
    REJECTED,
    SENT,
    RECEIVED,
    QUARANTINED,
    ARCHIVED
}
