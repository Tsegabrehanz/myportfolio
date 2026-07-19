package com.tsegabrehan.einvoicing.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * SRS 5. Data Model — EInvoice.
 * Stores the generated or received e-invoice document alongside its
 * compliance lifecycle state. The raw document bytes (XML or hybrid PDF)
 * are stored as a BLOB; in a production deployment this would more likely
 * be a reference into object storage (e.g. S3) rather than an inline column.
 */
@Entity
@Table(name = "einvoice")
public class EInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Foreign key into the upstream system's invoice/order record. */
    @Column(name = "source_invoice_number", nullable = false)
    private String sourceInvoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceFormat format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;

    @Lob
    @Column(name = "document_bytes")
    private byte[] documentBytes;

    @Column(name = "document_content_type")
    private String documentContentType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EInvoice() {
        // JPA
    }

    public EInvoice(String sourceInvoiceNumber, InvoiceFormat format, InvoiceDirection direction) {
        this.sourceInvoiceNumber = sourceInvoiceNumber;
        this.format = format;
        this.direction = direction;
        this.status = InvoiceStatus.DRAFT;
        this.createdAt = Instant.now();
    }

    public void attachDocument(byte[] bytes, String contentType) {
        this.documentBytes = bytes;
        this.documentContentType = contentType;
    }

    public void markValidated() {
        this.status = InvoiceStatus.VALIDATED;
    }

    public void markRejected() {
        this.status = InvoiceStatus.REJECTED;
    }

    public void markSent() {
        this.status = InvoiceStatus.SENT;
    }

    public void markQuarantined() {
        this.status = InvoiceStatus.QUARANTINED;
    }

    public void markArchived() {
        this.status = InvoiceStatus.ARCHIVED;
    }

    public String getId() {
        return id;
    }

    public String getSourceInvoiceNumber() {
        return sourceInvoiceNumber;
    }

    public InvoiceFormat getFormat() {
        return format;
    }

    public InvoiceDirection getDirection() {
        return direction;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public byte[] getDocumentBytes() {
        return documentBytes;
    }

    public String getDocumentContentType() {
        return documentContentType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
