package com.tsegabrehan.einvoicing.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * SRS 5. Data Model — ValidationResult.
 * Records the outcome of running an EInvoice through
 * {@link com.tsegabrehan.einvoicing.validation.En16931Validator} (FR-6, FR-7, FR-8).
 */
@Entity
@Table(name = "validation_result")
public class ValidationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "einvoice_id", nullable = false)
    private String eInvoiceId;

    @Column(name = "rule_set_version", nullable = false)
    private String ruleSetVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ValidationOutcome outcome;

    @ElementCollection
    @CollectionTable(name = "validation_result_details", joinColumns = @JoinColumn(name = "validation_result_id"))
    @Column(name = "detail")
    private List<String> details = new ArrayList<>();

    @Column(name = "validated_at", nullable = false)
    private Instant validatedAt;

    protected ValidationResult() {
        // JPA
    }

    public ValidationResult(String eInvoiceId, String ruleSetVersion, ValidationOutcome outcome, List<String> details) {
        this.eInvoiceId = eInvoiceId;
        this.ruleSetVersion = ruleSetVersion;
        this.outcome = outcome;
        this.details = details != null ? details : new ArrayList<>();
        this.validatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getEInvoiceId() {
        return eInvoiceId;
    }

    public String getRuleSetVersion() {
        return ruleSetVersion;
    }

    public ValidationOutcome getOutcome() {
        return outcome;
    }

    public List<String> getDetails() {
        return details;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public boolean isCompliant() {
        return outcome == ValidationOutcome.PASS;
    }
}
