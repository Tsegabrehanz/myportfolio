package com.tsegabrehan.einvoicing.generation;

import com.tsegabrehan.einvoicing.domain.Party;
import com.tsegabrehan.einvoicing.domain.SourceInvoice;
import com.tsegabrehan.einvoicing.domain.SourceInvoiceLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * These cases were first written and run as a standalone (no-Maven)
 * verification harness in the sandbox this project was built in, since
 * Maven Central wasn't reachable there to pull in JUnit — see the
 * project README's "How this was actually verified" section. They're
 * reproduced here as proper JUnit 5 tests for normal {@code mvn test} use.
 */
class XRechnungGeneratorTest {

    private final XRechnungGenerator generator = new XRechnungGenerator();

    private SourceInvoice validInvoice() {
        Party seller = new Party("Tsegabrehan Software GmbH", "DE123456789",
                "Allendorfer Straße 20B", "60433", "Frankfurt am Main", "DE");
        Party buyer = new Party("Musterkunde AG", "DE987654321",
                "Musterstraße 1", "10115", "Berlin", "DE");
        SourceInvoiceLine line1 = new SourceInvoiceLine("1", "Consulting services - July",
                new BigDecimal("10"), new BigDecimal("150.00"), "S", new BigDecimal("19.00"));
        SourceInvoiceLine line2 = new SourceInvoiceLine("2", "Software license",
                new BigDecimal("1"), new BigDecimal("500.00"), "S", new BigDecimal("19.00"));
        return new SourceInvoice("INV-2026-0001", LocalDate.of(2026, 7, 15), "EUR",
                seller, buyer, List.of(line1, line2), "Payment due within 30 days");
    }

    @Test
    void generatesWellFormedXmlWithCorrectTotals() {
        byte[] xml = generator.generate(validInvoice());
        String xmlStr = new String(xml, StandardCharsets.UTF_8);

        assertTrue(xml.length > 0);
        assertTrue(xmlStr.contains("INV-2026-0001"));
        assertTrue(xmlStr.contains("DE123456789"));
        // net: 10*150.00 + 1*500.00 = 2000.00; VAT 19% = 380.00; gross = 2380.00
        assertTrue(xmlStr.contains("2000.00"), "net total should be present");
        assertTrue(xmlStr.contains("380.00"), "VAT amount should be present");
        assertTrue(xmlStr.contains("2380.00"), "VAT-inclusive total should be present");
    }

    @Test
    void preservesNonAsciiCharactersAsUtf8() {
        byte[] xml = generator.generate(validInvoice());
        String xmlStr = new String(xml, StandardCharsets.UTF_8);
        assertTrue(xmlStr.contains("Allendorfer Straße 20B"));
    }

    @Test
    void rejectsGenerationWhenSellerVatIdMissing() {
        Party sellerMissingVat = new Party("Tsegabrehan Software GmbH", null,
                "Allendorfer Straße 20B", "60433", "Frankfurt am Main", "DE");
        Party buyer = new Party("Musterkunde AG", "DE987654321", "Musterstraße 1", "10115", "Berlin", "DE");
        SourceInvoiceLine line = new SourceInvoiceLine("1", "Consulting", BigDecimal.TEN,
                new BigDecimal("100"), "S", new BigDecimal("19"));
        SourceInvoice invoice = new SourceInvoice("INV-2026-0002", LocalDate.now(), "EUR",
                sellerMissingVat, buyer, List.of(line), null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> generator.generate(invoice));
        assertTrue(ex.getMessage().contains("seller.vatId"));
    }

    @Test
    void rejectsGenerationWhenNoLinesPresent() {
        Party seller = new Party("Seller GmbH", "DE111111111", "Street 1", "10000", "City", "DE");
        Party buyer = new Party("Buyer AG", "DE222222222", "Street 2", "20000", "City", "DE");
        SourceInvoice invoice = new SourceInvoice("INV-2026-0004", LocalDate.now(), "EUR",
                seller, buyer, List.of(), null);

        assertThrows(IllegalArgumentException.class, () -> generator.generate(invoice));
    }

    @Test
    void checkRequiredFieldsReturnsFieldLevelErrorsWithoutThrowing() {
        List<String> errors = generator.checkRequiredFields(null);
        assertFalse(errors.isEmpty());
    }
}
