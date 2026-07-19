package com.tsegabrehan.einvoicing.service;

import com.tsegabrehan.einvoicing.domain.*;
import com.tsegabrehan.einvoicing.generation.XRechnungGenerator;
import com.tsegabrehan.einvoicing.generation.ZugferdGenerator;
import com.tsegabrehan.einvoicing.parsing.XRechnungParser;
import com.tsegabrehan.einvoicing.repository.EInvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Orchestrates the outbound generation path: source data in, a validated
 * (or rejected) {@link EInvoice} out. Ties together
 * {@link XRechnungGenerator}, {@link ZugferdGenerator}, and
 * {@link ValidationService} (SRS FR-1 through FR-9, FR-15, FR-23).
 */
@Service
public class EInvoiceService {

    private final XRechnungGenerator xRechnungGenerator;
    private final ZugferdGenerator zugferdGenerator;
    private final XRechnungParser xRechnungParser;
    private final ValidationService validationService;
    private final EInvoiceRepository eInvoiceRepository;

    public EInvoiceService(XRechnungGenerator xRechnungGenerator,
                            ZugferdGenerator zugferdGenerator,
                            XRechnungParser xRechnungParser,
                            ValidationService validationService,
                            EInvoiceRepository eInvoiceRepository) {
        this.xRechnungGenerator = xRechnungGenerator;
        this.zugferdGenerator = zugferdGenerator;
        this.xRechnungParser = xRechnungParser;
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
        return generate(sourceInvoice, InvoiceFormat.XRECHNUNG,
                () -> xRechnungGenerator.generate(sourceInvoice), "application/xml");
    }

    /**
     * FR-2, FR-3: generates a ZUGFeRD hybrid PDF (with the same underlying
     * XRechnung XML embedded inside it) from source data, then validates
     * the embedded XML data the same way as the pure-XML path.
     */
    @Transactional
    public EInvoice generateZugferd(SourceInvoice sourceInvoice) {
        return generate(sourceInvoice, InvoiceFormat.ZUGFERD,
                () -> zugferdGenerator.generate(sourceInvoice), "application/pdf");
    }

    private EInvoice generate(SourceInvoice sourceInvoice, InvoiceFormat format,
                               java.util.function.Supplier<byte[]> generator, String contentType) {
        EInvoice invoice = new EInvoice(sourceInvoice.invoiceNumber(), format, InvoiceDirection.OUTGOING);
        invoice = eInvoiceRepository.save(invoice); // persist first to obtain an id for the validation record

        byte[] document;
        try {
            document = generator.get();
        } catch (IllegalArgumentException fieldError) {
            // FR-5: missing mandatory field — reject before ever attempting validation.
            invoice.markRejected();
            return eInvoiceRepository.save(invoice);
        }
        invoice.attachDocument(document, contentType);

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
     * FR-14, FR-15, FR-16: registers an incoming XRechnung XML document,
     * parsing it back into structured data with {@link XRechnungParser}
     * before validating it. Documents that fail to parse at all (not even
     * well-formed XRechnung) are quarantined with the parse failure as the
     * reason, per FR-16 — never silently discarded.
     * <p>
     * For incoming ZUGFeRD PDFs, extract the embedded XML first (see
     * {@code ZugferdParser}) and pass the extracted bytes here — this
     * method only handles the XML payload itself.
     */
    @Transactional
    public EInvoice registerIncomingXRechnung(byte[] rawXml) {
        EInvoice invoice;
        SourceInvoice sourceInvoice;
        try {
            sourceInvoice = xRechnungParser.parse(rawXml);
            invoice = new EInvoice(sourceInvoice.invoiceNumber(), InvoiceFormat.XRECHNUNG, InvoiceDirection.INCOMING);
        } catch (XRechnungParser.ParseException e) {
            // FR-16: flag and quarantine rather than silently discard — but we don't even
            // have an invoice number to key it under, since parsing failed before we could
            // extract one. Use a placeholder derived from content hash so it's still traceable.
            invoice = new EInvoice("UNPARSEABLE-" + Integer.toHexString(java.util.Arrays.hashCode(rawXml)),
                    InvoiceFormat.XRECHNUNG, InvoiceDirection.INCOMING);
            invoice.attachDocument(rawXml, "application/xml");
            invoice.markQuarantined();
            return eInvoiceRepository.save(invoice);
        }

        invoice.attachDocument(rawXml, "application/xml");
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
