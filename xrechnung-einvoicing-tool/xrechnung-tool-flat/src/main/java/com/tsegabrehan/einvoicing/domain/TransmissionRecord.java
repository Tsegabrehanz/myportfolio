package com.tsegabrehan.einvoicing.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * SRS 5. Data Model — TransmissionRecord (FR-10 through FR-13).
 */
@Entity
@Table(name = "transmission_record")
public class TransmissionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "einvoice_id", nullable = false)
    private String eInvoiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransmissionMethod method;

    @Column(nullable = false)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransmissionStatus status;

    @Column(nullable = false)
    private Instant timestamp;

    protected TransmissionRecord() {
        // JPA
    }

    public TransmissionRecord(String eInvoiceId, TransmissionMethod method, String recipient, TransmissionStatus status) {
        this.eInvoiceId = eInvoiceId;
        this.method = method;
        this.recipient = recipient;
        this.status = status;
        this.timestamp = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getEInvoiceId() {
        return eInvoiceId;
    }

    public TransmissionMethod getMethod() {
        return method;
    }

    public String getRecipient() {
        return recipient;
    }

    public TransmissionStatus getStatus() {
        return status;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
