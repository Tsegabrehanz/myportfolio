package com.tsegabrehan.einvoicing.generation;

import com.tsegabrehan.einvoicing.domain.*;
import com.tsegabrehan.einvoicing.parsing.ZugferdParser;
import com.tsegabrehan.einvoicing.validation.En16931Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * These tests found and pinned down a real bug during development: the
 * embedded-file stream object in the generated PDF was missing its
 * required {@code /Length} dictionary entry, which corrupted extraction —
 * confirmed independently with Poppler's {@code pdfdetach} (a real,
 * separate PDF implementation) failing with "Bad 'Length' attribute in
 * stream" and returning a corrupted attachment before the fix. See the
 * README's verification section for the full story, including that the
 * fix was cross-checked by comparing this project's own
 * {@link ZugferdParser} output byte-for-byte against Poppler's extraction
 * of the same file.
 */
class ZugferdGeneratorTest {

    private final ZugferdGenerator zugferdGenerator = new ZugferdGenerator();
    private final ZugferdParser zugferdParser = new ZugferdParser();

    private SourceInvoice sample() {
        Party seller = new Party("Tsegabrehan Software GmbH", "DE123456789",
                "Allendorfer Straße 20B", "60433", "Frankfurt am Main", "DE");
        Party buyer = new Party("Musterkunde AG", "DE987654321",
                "Musterstraße 1", "10115", "Berlin", "DE");
        SourceInvoiceLine line1 = new SourceInvoiceLine("1", "Consulting services - July",
                new BigDecimal("10"), new BigDecimal("150.00"), "S", new BigDecimal("19.00"));
        SourceInvoiceLine line2 = new SourceInvoiceLine("2", "Software license",
                new BigDecimal("1"), new BigDecimal("500.00"), "S", new BigDecimal("19.00"));
        return new SourceInvoice("INV-2026-0001", LocalDate.of(2026, 7, 15), "EUR",
                seller, buyer, List.of(line1, line2), "Net 30");
    }

    @Test
    void producesAWellFormedPdf() {
        byte[] pdf = zugferdGenerator.generate(sample());
        // Minimal structural sanity: PDF header and EOF marker present.
        String start = new String(pdf, 0, Math.min(8, pdf.length), java.nio.charset.StandardCharsets.US_ASCII);
        assertTrue(start.startsWith("%PDF-1."), "should start with a PDF header");
        String end = new String(pdf, Math.max(0, pdf.length - 6), Math.min(6, pdf.length), java.nio.charset.StandardCharsets.US_ASCII);
        assertTrue(end.contains("%%EOF"), "should end with the PDF EOF marker");
    }

    @Test
    void embeddedXmlCanBeExtractedAndIsWellFormed() {
        byte[] pdf = zugferdGenerator.generate(sample());
        byte[] xml = zugferdParser.extractEmbeddedXml(pdf);
        assertTrue(xml.length > 0);
        String xmlStr = new String(xml, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(xmlStr.startsWith("<?xml"), "extracted content should be XML, starting with the XML declaration");
        assertTrue(xmlStr.trim().endsWith("</Invoice>"), "extracted content should end cleanly at </Invoice> with no trailing PDF syntax");
    }

    @Test
    void fullRoundTripGenerateExtractParseValidate() {
        SourceInvoice original = sample();
        byte[] pdf = zugferdGenerator.generate(original);
        SourceInvoice parsed = zugferdParser.parse(pdf);

        assertEquals(original.invoiceNumber(), parsed.invoiceNumber());
        assertEquals(original.seller().vatId(), parsed.seller().vatId());
        assertEquals(2, parsed.lines().size());

        var result = new En16931Validator().validate(parsed);
        assertEquals(ValidationOutcome.PASS, result.outcome(), () -> "issues: " + result.issues());
    }

    @Test
    void rejectsExtractionFromNonPdfInput() {
        assertThrows(ZugferdParser.ExtractionException.class,
                () -> zugferdParser.extractEmbeddedXml("not a pdf at all".getBytes()));
    }

    @Test
    void embeddedFilenameFollowsZugferdConvention() {
        // ZUGFeRD 2.x / Factur-X convention: the embedded file is named factur-x.xml.
        assertEquals("factur-x.xml", ZugferdGenerator.EMBEDDED_XML_FILENAME);
    }
}
