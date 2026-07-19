package com.tsegabrehan.einvoicing.validation;

import com.tsegabrehan.einvoicing.domain.Party;
import com.tsegabrehan.einvoicing.domain.SourceInvoice;
import com.tsegabrehan.einvoicing.domain.SourceInvoiceLine;
import com.tsegabrehan.einvoicing.domain.ValidationOutcome;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class En16931ValidatorTest {

    private final En16931Validator validator = new En16931Validator();

    private SourceInvoice validInvoice() {
        Party seller = new Party("Tsegabrehan Software GmbH", "DE123456789", "Street 1", "60433", "Frankfurt", "DE");
        Party buyer = new Party("Musterkunde AG", "DE987654321", "Street 2", "10115", "Berlin", "DE");
        SourceInvoiceLine line1 = new SourceInvoiceLine("1", "Consulting services",
                new BigDecimal("10"), new BigDecimal("150.00"), "S", new BigDecimal("19.00"));
        return new SourceInvoice("INV-2026-0001", LocalDate.of(2026, 7, 15), "EUR",
                seller, buyer, List.of(line1), "Net 30");
    }

    @Test
    void validInvoicePasses() {
        var result = validator.validate(validInvoice());
        assertEquals(ValidationOutcome.PASS, result.outcome());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void nullInvoiceIsSyntaxError() {
        var result = validator.validate(null);
        assertEquals(ValidationOutcome.SYNTAX_ERROR, result.outcome());
    }

    @Test
    void missingInvoiceNumberIsSyntaxError() {
        SourceInvoice invoice = validInvoice();
        SourceInvoice broken = new SourceInvoice(null, invoice.issueDate(), invoice.currencyCode(),
                invoice.seller(), invoice.buyer(), invoice.lines(), invoice.paymentMeansText());
        var result = validator.validate(broken);
        assertEquals(ValidationOutcome.SYNTAX_ERROR, result.outcome());
    }

    @Test
    void exemptVatCategoryWithNonZeroRateIsBusinessRuleViolation() {
        Party seller = new Party("Seller GmbH", "DE123456789", "Street 1", "60433", "Frankfurt", "DE");
        Party buyer = new Party("Buyer AG", "DE987654321", "Street 2", "10115", "Berlin", "DE");
        // Category "E" (exempt) must carry a zero/absent rate; this wrongly carries 19%.
        SourceInvoiceLine badLine = new SourceInvoiceLine("1", "Exempt service",
                BigDecimal.ONE, new BigDecimal("100.00"), "E", new BigDecimal("19.00"));
        SourceInvoice invoice = new SourceInvoice("INV-2026-0003", LocalDate.now(), "EUR",
                seller, buyer, List.of(badLine), null);

        var result = validator.validate(invoice);
        assertEquals(ValidationOutcome.BUSINESS_RULE_VIOLATION, result.outcome());
        assertTrue(result.issues().stream().anyMatch(i -> i.contains("BR-S-06") || i.contains("BR-E-06") || i.contains("BR-AE-06")));
    }

    @Test
    void standardRateCategoryWithZeroRateIsBusinessRuleViolation() {
        Party seller = new Party("Seller GmbH", "DE123456789", "Street 1", "60433", "Frankfurt", "DE");
        Party buyer = new Party("Buyer AG", "DE987654321", "Street 2", "10115", "Berlin", "DE");
        SourceInvoiceLine badLine = new SourceInvoiceLine("1", "Standard-rate service",
                BigDecimal.ONE, new BigDecimal("100.00"), "S", BigDecimal.ZERO);
        SourceInvoice invoice = new SourceInvoice("INV-2026-0005", LocalDate.now(), "EUR",
                seller, buyer, List.of(badLine), null);

        var result = validator.validate(invoice);
        assertEquals(ValidationOutcome.BUSINESS_RULE_VIOLATION, result.outcome());
        assertTrue(result.issues().stream().anyMatch(i -> i.contains("BR-S-01")));
    }

    @Test
    void unrecognisedCurrencyIsBusinessRuleViolation() {
        SourceInvoice invoice = validInvoice();
        SourceInvoice badCurrency = new SourceInvoice(invoice.invoiceNumber(), invoice.issueDate(), "XYZ",
                invoice.seller(), invoice.buyer(), invoice.lines(), invoice.paymentMeansText());

        var result = validator.validate(badCurrency);
        assertEquals(ValidationOutcome.BUSINESS_RULE_VIOLATION, result.outcome());
    }

    @Test
    void malformedVatIdIsBusinessRuleViolation() {
        Party seller = new Party("Seller GmbH", "not-a-vat-id", "Street 1", "60433", "Frankfurt", "DE");
        Party buyer = new Party("Buyer AG", "DE987654321", "Street 2", "10115", "Berlin", "DE");
        SourceInvoiceLine line = new SourceInvoiceLine("1", "Service", BigDecimal.ONE,
                new BigDecimal("100.00"), "S", new BigDecimal("19.00"));
        SourceInvoice invoice = new SourceInvoice("INV-2026-0006", LocalDate.now(), "EUR",
                seller, buyer, List.of(line), null);

        var result = validator.validate(invoice);
        assertEquals(ValidationOutcome.BUSINESS_RULE_VIOLATION, result.outcome());
    }

    @Test
    void negativeQuantityIsBusinessRuleViolation() {
        Party seller = new Party("Seller GmbH", "DE123456789", "Street 1", "60433", "Frankfurt", "DE");
        Party buyer = new Party("Buyer AG", "DE987654321", "Street 2", "10115", "Berlin", "DE");
        SourceInvoiceLine line = new SourceInvoiceLine("1", "Service", new BigDecimal("-1"),
                new BigDecimal("100.00"), "S", new BigDecimal("19.00"));
        SourceInvoice invoice = new SourceInvoice("INV-2026-0007", LocalDate.now(), "EUR",
                seller, buyer, List.of(line), null);

        var result = validator.validate(invoice);
        assertEquals(ValidationOutcome.BUSINESS_RULE_VIOLATION, result.outcome());
    }
}
