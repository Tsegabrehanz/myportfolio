package com.tsegabrehan.einvoicing.domain;

/**
 * FR-8: the system must distinguish syntax errors (document is not a
 * valid e-invoice at all) from business-rule violations (structurally
 * valid but content-non-compliant, e.g. VAT totals not reconciling).
 */
public enum ValidationOutcome {
    PASS,
    SYNTAX_ERROR,
    BUSINESS_RULE_VIOLATION
}
