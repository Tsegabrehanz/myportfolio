package com.tsegabrehan.einvoicing.service;

import com.tsegabrehan.einvoicing.domain.*;
import com.tsegabrehan.einvoicing.generation.XRechnungGenerator;
import com.tsegabrehan.einvoicing.repository.EInvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Orchestrates the outbound generation path: source data in, a validated
 * (or rejected) {@link EInvoice} out. Ties together
 * {@link XRechnungGenerator} and {@link ValidationService}
 * (SRS FR-1 through FR-9, FR-23).
 * <p>
 * ZUGFeRD generation (FR-2) is not implemented in this build — producing a
 * true PDF/A-3 with embedded XML needs a PDF library capable of PDF/A-3
 * attachment embedding (e.g. Apache PDFBox with the PDF/A-3 profile, or
 * the Mustangproject library purpose-built for this). Neither could be
 * fetched from Maven Central in this sandbox (see README). The XRechnung
 * path is fully implemented and is, in practice, the lower-friction format
 * for a B2B sender anyway since it requires no PDF tooling at all.
 */
@Service
public class EInvoiceService {

    private final XRechnungGenerator xRechnungGenerator;
    private final ValidationService validationService;
    private final EInvoiceRepository eInvoiceRepository;

    public EInvoiceService(XRechnungGenerator xRechnungGenerator,
                            ValidationService validationService,
                            EInvoiceRepository eInvoiceRepository) {
        this.xRechnungGenerator = xRechnungGenerator;
        this.validationService = validationService;
        this.eInvoiceRepository = eInvoiceRepository;
    }

    /**
     * FR-1, FR-3, FR-5, FR-6: generates an XRechnung XML document from
     * source data, then immediately validates it and records the result.
     * The returned {@link EInvoice} carries its post-validation status —
     * callers should check {@link EInvoice#getStatus()} before treating it
     * as sendable, rather than assuming success.
     */
    @Transactional
    public EInvoice generateXRechnung(SourceInvoice sourceInvoice) {
        EInvoice invoice = new EInvoice(sourceInvoice.invoiceNumber(), InvoiceFormat.XRECHNUNG, InvoiceDirection.OUTGOING);
        invoice = eInvoiceRepository.save(invoice); // persist first to obtain an id for the validation record

        byte[] xml;
        try {
            xml = xRechnungGenerator.generate(sourceInvoice);
        } catch (IllegalArgumentException fieldError) {
            // FR-5: missing mandatory field — reject before ever attempting validation.
            invoice.markRejected();
            return eInvoiceRepository.save(invoice);
        }
        invoice.attachDocument(xml, "application/xml");

        ValidationResult result = validationService.validateAndRecord(invoice.getId(), sourceInvoice);
        if (result.isCompliant()) {
            invoice.markValidated();
        } else {
            // FR-9: never silently "fix" bad data — the invoice stays REJECTED
            // and the ValidationResult's details carry the specific issues.
            invoice.markRejected();
        }
        return eInvoiceRepository.save(invoice);
    }

    /**
     * FR-14, FR-15, FR-16: registers an incoming document already parsed
     * into {@link SourceInvoice} form (parsing itself — reading an
     * arbitrary uploaded XRechnung XML or ZUGFeRD PDF back into structured
     * data — is not implemented in this build; see README "What's not
     * implemented"). Validates it the same way as an outbound invoice and
     * quarantines it if it fails.
     */
    @Transactional
    public EInvoice registerIncoming(SourceInvoice sourceInvoice, InvoiceFormat format, byte[] rawDocument, String contentType) {
        EInvoice invoice = new EInvoice(sourceInvoice.invoiceNumber(), format, InvoiceDirection.INCOMING);
        invoice.attachDocument(rawDocument, contentType);
        invoice = eInvoiceRepository.save(invoice);

        ValidationResult result = validationService.validateAndRecord(invoice.getId(), sourceInvoice);
        if (result.isCompliant()) {
            invoice.markValidated();
        } else {
            invoice.markQuarantined();
        }
        return eInvoiceRepository.save(invoice);
    }

    public EInvoice getOrThrow(String id) {
        return eInvoiceRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No e-invoice with id " + id));
    }

    public Optional<EInvoice> get(String id) {
        return eInvoiceRepository.findById(id);
    }

    public List<EInvoice> findIncoming() {
        return eInvoiceRepository.findByDirection(InvoiceDirection.INCOMING);
    }

    public List<EInvoice> findByStatus(InvoiceStatus status) {
        return eInvoiceRepository.findByStatus(status);
    }

    public List<EInvoice> findAll() {
        return eInvoiceRepository.findAll();
    }
}
