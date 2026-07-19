package com.tsegabrehan.einvoicing.validation;

import com.tsegabrehan.einvoicing.domain.Party;
import com.tsegabrehan.einvoicing.domain.SourceInvoice;
import com.tsegabrehan.einvoicing.domain.SourceInvoiceLine;
import com.tsegabrehan.einvoicing.domain.ValidationOutcome;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates a {@link SourceInvoice} against a representative subset of
 * EN 16931 business rules (SRS FR-6, FR-7, FR-8).
 * <p>
 * <b>Scope note (read this before assuming full compliance):</b> the
 * official EN 16931 rule set, as implemented by KoSIT's Schematron files,
 * runs to well over one hundred rules (BR-1 through BR-CO-* and the
 * German XRechnung-specific extensions). Reproducing that rule set from
 * scratch is a multi-week effort in itself and is not something this
 * portfolio-scope build attempts. What is implemented here is a
 * deliberately chosen, genuinely-checked subset covering the rule
 * categories the SRS calls out explicitly (missing identification,
 * VAT category correctness, arithmetic reconciliation) — enough to
 * demonstrate the validation architecture (and the syntax vs.
 * business-rule distinction from FR-8) end to end.
 * <p>
 * For production use, swap this class's rule implementations for a real
 * Schematron engine run against KoSIT's published XRechnung rules (e.g.
 * via the Mustangproject library) — see the README.
 */
public class En16931Validator {

    /** Tracked independently of the application version, per NFR-1. */
    public static final String RULE_SET_VERSION = "portfolio-subset-2026.1";

    private static final Set<String> KNOWN_VAT_CATEGORIES = Set.of("S", "Z", "E", "AE", "K", "G", "O");
    private static final Set<String> EXEMPT_CATEGORIES_REQUIRING_ZERO_RATE = Set.of("Z", "E", "AE", "K", "G", "O");
    private static final Set<String> ISO_4217_SUBSET = Set.of("EUR", "USD", "GBP", "CHF");

    public record ValidationOutcomeResult(ValidationOutcome outcome, List<String> issues) {
    }

    /**
     * Runs the rule subset against the given invoice. Applies to both
     * outbound (pre-send) and inbound (post-receive) validation, per
     * FR-6 / FR-7 — the same rule set either way.
     */
    public ValidationOutcomeResult validate(SourceInvoice invoice) {
        List<String> syntaxIssues = new ArrayList<>();
        List<String> businessRuleIssues = new ArrayList<>();

        // --- "Syntax" tier: is this even a well-formed invoice at all? ---
        if (invoice == null) {
            syntaxIssues.add("BR-1: an Invoice shall have an Invoice number, an Invoice issue date... "
                    + "(no invoice data supplied at all)");
            return new ValidationOutcomeResult(ValidationOutcome.SYNTAX_ERROR, syntaxIssues);
        }
        if (isBlank(invoice.invoiceNumber())) {
            syntaxIssues.add("BR-2: an Invoice shall have an Invoice number (BT-1).");
        }
        if (invoice.issueDate() == null) {
            syntaxIssues.add("BR-3: an Invoice shall have an Invoice issue date (BT-2).");
        }
        if (invoice.seller() == null) {
            syntaxIssues.add("BR-6: an Invoice shall contain the Seller name (BT-27) — no seller party present.");
        }
        if (invoice.buyer() == null) {
            syntaxIssues.add("BR-7: an Invoice shall contain the Buyer name (BT-44) — no buyer party present.");
        }
        if (invoice.lines() == null || invoice.lines().isEmpty()) {
            syntaxIssues.add("BR-16: an Invoice shall have at least one Invoice line (BG-25).");
        }

        if (!syntaxIssues.isEmpty()) {
            // Structurally broken; do not proceed to business-rule (arithmetic/semantic) checks.
            return new ValidationOutcomeResult(ValidationOutcome.SYNTAX_ERROR, syntaxIssues);
        }

        // --- Business-rule tier: structurally present, now check correctness ---
        businessRuleIssues.addAll(checkParty("BR-6/BR-8", "seller", invoice.seller()));
        businessRuleIssues.addAll(checkParty("BR-7/BR-9", "buyer", invoice.buyer()));

        if (isBlank(invoice.currencyCode())) {
            businessRuleIssues.add("BR-5: an Invoice shall have an Invoice currency code (BT-5).");
        } else if (!ISO_4217_SUBSET.contains(invoice.currencyCode())) {
            businessRuleIssues.add("BR-CL-03: Invoice currency code (BT-5) '" + invoice.currencyCode()
                    + "' is not in the recognised subset checked by this validator ("
                    + ISO_4217_SUBSET + "). Extend ISO_4217_SUBSET for additional currencies.");
        }

        int index = 0;
        for (SourceInvoiceLine line : invoice.lines()) {
            businessRuleIssues.addAll(checkLine(index, line));
            index++;
        }

        businessRuleIssues.addAll(checkArithmetic(invoice));

        if (!businessRuleIssues.isEmpty()) {
            return new ValidationOutcomeResult(ValidationOutcome.BUSINESS_RULE_VIOLATION, businessRuleIssues);
        }
        return new ValidationOutcomeResult(ValidationOutcome.PASS, List.of());
    }

    private List<String> checkParty(String ruleRef, String label, Party party) {
        List<String> issues = new ArrayList<>();
        if (isBlank(party.name())) {
            issues.add(ruleRef + ": " + label + " name is required.");
        }
        if (isBlank(party.vatId())) {
            issues.add(ruleRef + ": " + label + " VAT identifier is required.");
        } else if (!party.vatId().matches("^[A-Z]{2}[A-Za-z0-9]{2,12}$")) {
            issues.add("BR-CO-09: " + label + " VAT identifier '" + party.vatId()
                    + "' does not match the expected pattern (2-letter country prefix + identifier).");
        }
        if (isBlank(party.countryCode())) {
            issues.add(ruleRef + ": " + label + " country code is required.");
        } else if (!party.countryCode().matches("^[A-Z]{2}$")) {
            issues.add("BR-CO-09: " + label + " country code '" + party.countryCode()
                    + "' is not a valid ISO 3166-1 alpha-2 code.");
        }
        return issues;
    }

    private List<String> checkLine(int index, SourceInvoiceLine line) {
        List<String> issues = new ArrayList<>();
        String ref = "line[" + index + "]";

        if (line.quantity() == null || line.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            issues.add("BR-27: " + ref + " Invoiced quantity (BT-129) shall be present and greater than zero.");
        }
        if (line.unitPrice() == null || line.unitPrice().compareTo(BigDecimal.ZERO) < 0) {
            issues.add("BR-28: " + ref + " Item net price (BT-146) shall be present and shall not be negative.");
        }
        if (isBlank(line.vatCategoryCode())) {
            issues.add("BR-CO-4: " + ref + " VAT category code (BT-151) is required.");
        } else if (!KNOWN_VAT_CATEGORIES.contains(line.vatCategoryCode())) {
            issues.add("BR-CO-4: " + ref + " VAT category code '" + line.vatCategoryCode()
                    + "' is not one of the recognised UNTDID 5305 codes " + KNOWN_VAT_CATEGORIES + ".");
        } else {
            boolean mustBeZero = EXEMPT_CATEGORIES_REQUIRING_ZERO_RATE.contains(line.vatCategoryCode());
            boolean rateIsZeroOrAbsent = line.vatRatePercent() == null
                    || line.vatRatePercent().compareTo(BigDecimal.ZERO) == 0;
            if (mustBeZero && !rateIsZeroOrAbsent) {
                issues.add("BR-S-06/BR-E-06/BR-AE-06: " + ref + " VAT category '" + line.vatCategoryCode()
                        + "' requires a zero or absent VAT rate, but " + line.vatRatePercent() + " was given.");
            }
            if ("S".equals(line.vatCategoryCode()) && rateIsZeroOrAbsent) {
                issues.add("BR-S-01: " + ref + " VAT category 'S' (standard rate) requires a positive VAT rate.");
            }
        }
        return issues;
    }

    private List<String> checkArithmetic(SourceInvoice invoice) {
        List<String> issues = new ArrayList<>();
        // BR-CO-10: sum of Invoice line net amounts = the invoice's total line net amount.
        // Here we simply confirm every line's net amount is internally consistent
        // (quantity * unitPrice), since totals are computed FROM the lines by this
        // service rather than supplied independently — the risk BR-CO-10 guards
        // against in a real system where totals arrive pre-computed from elsewhere.
        for (int i = 0; i < invoice.lines().size(); i++) {
            SourceInvoiceLine line = invoice.lines().get(i);
            if (line.quantity() != null && line.unitPrice() != null) {
                BigDecimal expected = line.quantity().multiply(line.unitPrice());
                BigDecimal actual = line.netAmount();
                if (actual == null || expected.compareTo(actual) != 0) {
                    issues.add("BR-CO-10: line[" + i + "] net amount does not equal quantity * unit price.");
                }
            }
        }
        return issues;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
