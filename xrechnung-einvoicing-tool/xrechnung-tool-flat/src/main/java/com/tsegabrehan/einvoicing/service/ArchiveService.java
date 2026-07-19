package com.tsegabrehan.einvoicing.service;

import com.tsegabrehan.einvoicing.domain.ArchiveEntry;
import com.tsegabrehan.einvoicing.domain.EInvoice;
import com.tsegabrehan.einvoicing.repository.ArchiveEntryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * FR-17, FR-18, FR-19: archives every generated/received e-invoice for the
 * 8-year retention period, immutably.
 * <p>
 * This service itself adds a second layer of protection on top of
 * {@link ArchiveEntryRepository} already lacking delete methods: it
 * refuses to archive the same {@code eInvoiceId} twice (an archive entry,
 * once created, is never replaced — only annotated).
 */
@Service
public class ArchiveService {

    private final ArchiveEntryRepository archiveEntryRepository;

    public ArchiveService(ArchiveEntryRepository archiveEntryRepository) {
        this.archiveEntryRepository = archiveEntryRepository;
    }

    public ArchiveEntry archive(EInvoice invoice) {
        Optional<ArchiveEntry> existing = archiveEntryRepository.findByEInvoiceId(invoice.getId());
        if (existing.isPresent()) {
            throw new IllegalStateException(
                    "E-invoice " + invoice.getId() + " is already archived; archive entries cannot be replaced (FR-18).");
        }
        invoice.markArchived();
        ArchiveEntry entry = new ArchiveEntry(invoice.getId());
        return archiveEntryRepository.save(entry);
    }

    /** FR-18: append-only annotation, e.g. "reviewed" or "disputed". Never a rewrite. */
    public ArchiveEntry annotate(String eInvoiceId, String annotation) {
        ArchiveEntry entry = archiveEntryRepository.findByEInvoiceId(eInvoiceId)
                .orElseThrow(() -> new java.util.NoSuchElementException(
                        "No archive entry for e-invoice " + eInvoiceId));
        entry.addAnnotation(annotation);
        return archiveEntryRepository.save(entry);
    }

    public Optional<ArchiveEntry> find(String eInvoiceId) {
        return archiveEntryRepository.findByEInvoiceId(eInvoiceId);
    }

    public List<ArchiveEntry> findAll() {
        return archiveEntryRepository.findAll();
    }
}
