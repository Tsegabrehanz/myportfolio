package com.tsegabrehan.einvoicing.parsing;

import com.tsegabrehan.einvoicing.domain.*;
import com.tsegabrehan.einvoicing.generation.XRechnungGenerator;
import com.tsegabrehan.einvoicing.validation.En16931Validator;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * These tests found a real bug during development: {@link XRechnungGenerator}
 * was creating every {@code cac:}/{@code cbc:} element under the root
 * {@code Invoice-2} namespace instead of the correct
 * {@code CommonAggregateComponents-2}/{@code CommonBasicComponents-2}
 * namespaces — invisible to a substring-based test, but exactly what a
 * namespace-aware parser (this one, or a real EN 16931 validator) would
 * catch. {@link #generatorProducesCorrectNamespaces()} pins that fix down.
 */
class XRechnungParserTest {

    private final XRechnungGenerator generator = new XRechnungGenerator();
    private final XRechnungParser parser = new XRechnungParser();

    private SourceInvoice sample() {
        Party seller = new Party("Tsegabrehan Software GmbH", "DE123456789",
                "Allendorfer Straße 20B", "60433", "Frankfurt am Main", "DE");
        Party buyer = new Party("Musterkunde AG", "DE987654321",
                "Musterstraße 1", "10115", "Berlin", "DE");
        SourceInvoiceLine line1 = new SourceInvoiceLine("1", "Consulting services",
                new BigDecimal("10"), new BigDecimal("150.00"), "S", new BigDecimal("19.00"));
        SourceInvoiceLine line2 = new SourceInvoiceLine("2", "Software license",
                new BigDecimal("1"), new BigDecimal("500.00"), "S", new BigDecimal("19.00"));
        return new SourceInvoice("INV-2026-0001", LocalDate.of(2026, 7, 15), "EUR",
                seller, buyer, List.of(line1, line2), "Net 30");
    }

    @Test
    void generatorProducesCorrectNamespaces() throws Exception {
        byte[] xml = generator.generate(sample());
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml));

        assertTrue(doc.getElementsByTagNameNS(
                "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2", "ID").getLength() > 0,
                "cbc:ID must live in the real CommonBasicComponents-2 namespace");
        assertTrue(doc.getElementsByTagNameNS(
                "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2", "Party").getLength() > 0,
                "cac:Party must live in the real CommonAggregateComponents-2 namespace");
    }

    @Test
    void roundTripsAllCoreFields() {
        SourceInvoice original = sample();
        byte[] xml = generator.generate(original);
        SourceInvoice parsed = parser.parse(xml);

        assertEquals(original.invoiceNumber(), parsed.invoiceNumber());
        assertEquals(original.issueDate(), parsed.issueDate());
        assertEquals(original.currencyCode(), parsed.currencyCode());
        assertEquals(original.seller().vatId(), parsed.seller().vatId());
        assertEquals(original.buyer().vatId(), parsed.buyer().vatId());
        assertEquals(2, parsed.lines().size());
        assertEquals(0, parsed.lines().get(0).quantity().compareTo(new BigDecimal("10")));
        assertEquals(0, parsed.lines().get(0).unitPrice().compareTo(new BigDecimal("150.00")));
        assertEquals("S", parsed.lines().get(0).vatCategoryCode());
    }

    @Test
    void roundTrippedInvoiceStillPassesValidation() {
        byte[] xml = generator.generate(sample());
        SourceInvoice parsed = parser.parse(xml);
        var result = new En16931Validator().validate(parsed);
        assertEquals(ValidationOutcome.PASS, result.outcome(), () -> "issues: " + result.issues());
    }

    @Test
    void rejectsNonXmlInput() {
        assertThrows(XRechnungParser.ParseException.class,
                () -> parser.parse("not even xml".getBytes()));
    }

    @Test
    void rejectsDoctypeDeclarations_XxeHardening() {
        String xxePayload = "<?xml version=\"1.0\"?>\n"
                + "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>\n"
                + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\">"
                + "<cbc:ID xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\">&xxe;</cbc:ID>"
                + "</Invoice>";
        assertThrows(XRechnungParser.ParseException.class,
                () -> parser.parse(xxePayload.getBytes()));
    }

    @Test
    void rejectsDocumentMissingInvoiceNumber() {
        String noIdPayload = "<?xml version=\"1.0\"?>"
                + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\"/>";
        assertThrows(XRechnungParser.ParseException.class,
                () -> parser.parse(noIdPayload.getBytes()));
    }
}
