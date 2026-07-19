package com.tsegabrehan.einvoicing.repository;

import com.tsegabrehan.einvoicing.domain.ArchiveEntry;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * FR-18 / NFR-3: archived invoices must be immutable for the 8-year
 * retention period — no update or delete operation shall be permitted.
 * <p>
 * This interface deliberately extends the bare {@link Repository} marker
 * rather than {@code JpaRepository} or {@code CrudRepository}, so that
 * {@code deleteById}, {@code delete}, and {@code deleteAll} are simply not
 * present on the type at all — not merely "discouraged," but absent from
 * the API surface a caller (or a future developer) could reach for.
 * <p>
 * {@code save} remains available because it is also how the one permitted
 * mutation — appending an annotation via {@link ArchiveEntry#addAnnotation}
 * — is persisted; the entity itself has no setters for its immutable fields
 * (document reference, retention date), so a {@code save} after construction
 * can only ever add annotations, never rewrite archived content.
 * <p>
 * In a production deployment this application-level guarantee should be
 * backed by a database-level control as well (e.g. a REVOKE UPDATE/DELETE
 * grant on the table, or WORM-capable storage) — see the README's
 * "What's simplified" section.
 */
public interface ArchiveEntryRepository extends Repository<ArchiveEntry, String> {

    ArchiveEntry save(ArchiveEntry entry);

    Optional<ArchiveEntry> findById(String id);

    Optional<ArchiveEntry> findByEInvoiceId(String eInvoiceId);

    List<ArchiveEntry> findAll();
}
