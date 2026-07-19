package com.tsegabrehan.einvoicing.api;

import com.tsegabrehan.einvoicing.api.dto.*;
import com.tsegabrehan.einvoicing.domain.*;
import com.tsegabrehan.einvoicing.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Implements the API surface specified in SRS Section 6.1.
 */
@RestController
@RequestMapping("/api/v1/einvoices")
public class EInvoiceController {

    private final EInvoiceService eInvoiceService;
    private final ValidationService validationService;
    private final TransmissionService transmissionService;
    private final ArchiveService archiveService;
    private final ComplianceDashboardService dashboardService;

    public EInvoiceController(EInvoiceService eInvoiceService,
                               ValidationService validationService,
                               TransmissionService transmissionService,
                               ArchiveService archiveService,
                               ComplianceDashboardService dashboardService) {
        this.eInvoiceService = eInvoiceService;
        this.validationService = validationService;
        this.transmissionService = transmissionService;
        this.archiveService = archiveService;
        this.dashboardService = dashboardService;
    }

    /** FR-1, FR-2, FR-3, FR-5, FR-6: generate + validate an e-invoice (XRechnung or ZUGFeRD). */
    @PostMapping
    public ResponseEntity<InvoiceMapper.EInvoiceResponse> generate(
            @Valid @RequestBody GenerateInvoiceRequest request,
            @RequestParam(name = "format", defaultValue = "XRECHNUNG") InvoiceFormat format) {
        SourceInvoice sourceInvoice = InvoiceMapper.toDomain(request);
        EInvoice invoice = format == InvoiceFormat.ZUGFERD
                ? eInvoiceService.generateZugferd(sourceInvoice)
                : eInvoiceService.generateXRechnung(sourceInvoice);
        HttpStatus status = invoice.getStatus() == InvoiceStatus.REJECTED ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(InvoiceMapper.EInvoiceResponse.from(invoice));
    }

    /**
     * FR-14, FR-15, FR-16: register an incoming XRechnung XML document.
     * For an incoming ZUGFeRD PDF, extract the embedded XML client-side
     * (or via a future {@code /incoming/zugferd} endpoint) and post the
     * extracted XML here — this endpoint validates and parses XML payloads.
     */
    @PostMapping(value = "/incoming", consumes = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<InvoiceMapper.EInvoiceResponse> receiveXRechnung(@RequestBody byte[] rawXml) {
        EInvoice invoice = eInvoiceService.registerIncomingXRechnung(rawXml);
        HttpStatus status = invoice.getStatus() == InvoiceStatus.QUARANTINED ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(InvoiceMapper.EInvoiceResponse.from(invoice));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceMapper.EInvoiceResponse> get(@PathVariable String id) {
        return ResponseEntity.ok(InvoiceMapper.EInvoiceResponse.from(eInvoiceService.getOrThrow(id)));
    }

    /** Retrieve the generated/received document bytes. */
    @GetMapping("/{id}/document")
    public ResponseEntity<byte[]> getDocument(@PathVariable String id) {
        EInvoice invoice = eInvoiceService.getOrThrow(id);
        if (invoice.getDocumentBytes() == null) {
            return ResponseEntity.notFound().build();
        }
        MediaType mediaType = invoice.getFormat() == InvoiceFormat.XRECHNUNG
                ? MediaType.APPLICATION_XML
                : MediaType.APPLICATION_PDF;
        return ResponseEntity.ok().contentType(mediaType).body(invoice.getDocumentBytes());
    }

    /** FR-6/FR-7/FR-8: most recent validation result for an e-invoice. */
    @GetMapping("/{id}/validation")
    public ResponseEntity<InvoiceMapper.ValidationResponse> validation(@PathVariable String id) {
        return validationService.history(id).stream()
                .max(java.util.Comparator.comparing(ValidationResult::getValidatedAt))
                .map(InvoiceMapper.ValidationResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** FR-10, FR-11, FR-12, FR-13: transmit a validated e-invoice. */
    @PostMapping("/{id}/send")
    public ResponseEntity<InvoiceMapper.TransmissionResponse> send(@PathVariable String id,
                                                                      @Valid @RequestBody SendInvoiceRequest request) {
        EInvoice invoice = eInvoiceService.getOrThrow(id);
        if (invoice.getStatus() != InvoiceStatus.VALIDATED) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build(); // FR-6: only validated invoices may be sent
        }
        TransmissionRecord record = request.method == TransmissionMethod.EMAIL
                ? transmissionService.sendViaEmail(invoice, request.recipient)
                : transmissionService.sendViaPeppol(invoice, request.recipient);

        if (record.getStatus() == TransmissionStatus.SENT) {
            invoice.markSent();
        }
        return ResponseEntity.ok(InvoiceMapper.TransmissionResponse.from(record));
    }

    /** FR-14: list received e-invoices. */
    @GetMapping("/incoming")
    public List<InvoiceMapper.EInvoiceResponse> incoming() {
        return eInvoiceService.findIncoming().stream().map(InvoiceMapper.EInvoiceResponse::from).toList();
    }

    /** FR-17, FR-19: archive an e-invoice (immutably; see ArchiveService/ArchiveEntryRepository). */
    @PostMapping("/{id}/archive")
    public ResponseEntity<Void> archive(@PathVariable String id) {
        EInvoice invoice = eInvoiceService.getOrThrow(id);
        archiveService.archive(invoice);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/archive")
    public ResponseEntity<ArchiveEntry> getArchive(@PathVariable String id) {
        return archiveService.find(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** FR-20, FR-21, FR-22: compliance dashboard summary. */
    @GetMapping("/dashboard")
    public ComplianceDashboardService.DashboardSummary dashboard() {
        return dashboardService.summarize();
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
}
