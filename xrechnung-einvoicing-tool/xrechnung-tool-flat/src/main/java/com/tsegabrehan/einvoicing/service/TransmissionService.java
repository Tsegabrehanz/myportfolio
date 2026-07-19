package com.tsegabrehan.einvoicing.service;

import com.tsegabrehan.einvoicing.domain.EInvoice;
import com.tsegabrehan.einvoicing.domain.TransmissionMethod;
import com.tsegabrehan.einvoicing.domain.TransmissionRecord;
import com.tsegabrehan.einvoicing.domain.TransmissionStatus;
import com.tsegabrehan.einvoicing.repository.TransmissionRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FR-10 through FR-13: transmits a validated e-invoice via email or Peppol,
 * and records the outcome.
 * <p>
 * The email path is a real, working implementation (Spring's
 * {@link JavaMailSender}, configured via {@code application.yml}).
 * <p>
 * The Peppol path is intentionally a documented stub, not a working
 * integration. Sending over the Peppol network requires a certified Access
 * Point — either self-hosting an AS4 endpoint (a significant undertaking
 * requiring PKI certificates issued by OpenPeppol) or contracting a
 * third-party certified provider and integrating with their API. Both are
 * infrastructure/organizational decisions the SRS explicitly leaves open
 * (see SRS Section 9, Open Questions) and neither is something a portfolio
 * project can meaningfully fake without being misleading about what's
 * actually implemented. {@link PeppolAccessPointClient} defines the
 * integration seam so a real client can be dropped in later.
 */
@Service
public class TransmissionService {

    private static final Logger log = LoggerFactory.getLogger(TransmissionService.class);

    private final JavaMailSender mailSender;
    private final PeppolAccessPointClient peppolClient;
    private final TransmissionRecordRepository transmissionRecordRepository;

    @Value("${einvoicing.mail.from:invoicing@example.com}")
    private String fromAddress;

    public TransmissionService(JavaMailSender mailSender,
                                PeppolAccessPointClient peppolClient,
                                TransmissionRecordRepository transmissionRecordRepository) {
        this.mailSender = mailSender;
        this.peppolClient = peppolClient;
        this.transmissionRecordRepository = transmissionRecordRepository;
    }

    public TransmissionRecord sendViaEmail(EInvoice invoice, String recipientEmail) {
        TransmissionStatus status;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipientEmail);
            helper.setSubject("Invoice " + invoice.getSourceInvoiceNumber());
            helper.setText("Please find the attached e-invoice, invoice number "
                    + invoice.getSourceInvoiceNumber() + ".");
            String filename = invoice.getFormat() == com.tsegabrehan.einvoicing.domain.InvoiceFormat.XRECHNUNG
                    ? invoice.getSourceInvoiceNumber() + ".xml"
                    : invoice.getSourceInvoiceNumber() + ".pdf";
            helper.addAttachment(filename, new org.springframework.core.io.ByteArrayResource(invoice.getDocumentBytes()));

            mailSender.send(message);
            status = TransmissionStatus.SENT;
        } catch (Exception e) {
            log.error("Failed to send e-invoice {} via email to {}", invoice.getId(), recipientEmail, e);
            status = TransmissionStatus.FAILED;
        }
        TransmissionRecord record = new TransmissionRecord(invoice.getId(), TransmissionMethod.EMAIL, recipientEmail, status);
        return transmissionRecordRepository.save(record);
    }

    public TransmissionRecord sendViaPeppol(EInvoice invoice, String peppolParticipantId) {
        TransmissionStatus status;
        try {
            peppolClient.send(peppolParticipantId, invoice.getDocumentBytes(), invoice.getDocumentContentType());
            status = TransmissionStatus.SENT;
        } catch (UnsupportedOperationException e) {
            log.warn("Peppol transmission requested for e-invoice {} but no Access Point is configured "
                    + "in this environment (see PeppolAccessPointClient javadoc).", invoice.getId());
            status = TransmissionStatus.FAILED;
        } catch (Exception e) {
            log.error("Failed to send e-invoice {} via Peppol to {}", invoice.getId(), peppolParticipantId, e);
            status = TransmissionStatus.FAILED;
        }
        TransmissionRecord record = new TransmissionRecord(invoice.getId(), TransmissionMethod.PEPPOL, peppolParticipantId, status);
        return transmissionRecordRepository.save(record);
    }
}
