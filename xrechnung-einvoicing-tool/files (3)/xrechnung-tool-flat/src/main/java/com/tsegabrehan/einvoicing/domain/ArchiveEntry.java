package com.tsegabrehan.einvoicing.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * SRS 5. Data Model — ArchiveEntry (FR-17, FR-18, FR-19; NFR-3).
 * <p>
 * By design this entity exposes no setters for {@code storedDocumentRef} or
 * {@code retentionExpiresAt} after construction, and
 * {@code com.tsegabrehan.einvoicing.repository.ArchiveEntryRepository}
 * deliberately does not expose delete operations against it.
 * Only {@link #addAnnotation(String)} may mutate an existing row, and that
 * only appends — it never rewrites or removes prior content (FR-18).
 */
@Entity
@Table(name = "archive_entry")
public class ArchiveEntry {

    /** 8 years, per SRS 1.3 retention requirement. */
    public static final int RETENTION_YEARS = 8;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "einvoice_id", nullable = false, unique = true)
    private String eInvoiceId;

    @Column(name = "archived_at", nullable = false)
    private Instant archivedAt;

    @Column(name = "retention_expires_at", nullable = false)
    private Instant retentionExpiresAt;

    @ElementCollection
    @CollectionTable(name = "archive_entry_annotations", joinColumns = @JoinColumn(name = "archive_entry_id"))
    @Column(name = "annotation")
    private List<String> annotations = new ArrayList<>();

    protected ArchiveEntry() {
        // JPA
    }

    public ArchiveEntry(String eInvoiceId) {
        this.eInvoiceId = eInvoiceId;
        this.archivedAt = Instant.now();
        this.retentionExpiresAt = this.archivedAt.plus(java.time.Duration.ofDays(365L * RETENTION_YEARS));
    }

    /** Append-only annotation (e.g. "reviewed", "disputed") — never a rewrite (FR-18). */
    public void addAnnotation(String annotation) {
        this.annotations.add(annotation);
    }

    public String getId() {
        return id;
    }

    public String getEInvoiceId() {
        return eInvoiceId;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public Instant getRetentionExpiresAt() {
        return retentionExpiresAt;
    }

    public List<String> getAnnotations() {
        return List.copyOf(annotations);
    }
}
