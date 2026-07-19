# Software Requirements Specification
## XRechnung / ZUGFeRD E-Invoicing Compliance Tool

**Version:** 1.0
**Stack:** Java EE / Spring Boot · PostgreSQL
**Relationship to portfolio:** Extends the Financial & Inventory Management Platform's invoicing module with EN 16931-compliant structured e-invoicing.

---

## 1. Introduction

### 1.1 Purpose
This document specifies requirements for a service that generates, validates, transmits, receives, and archives electronic invoices compliant with the German B2B e-invoicing mandate — supporting both the XRechnung (pure XML) and ZUGFeRD (hybrid PDF/A-3 with embedded XML) formats defined under the European standard EN 16931.

### 1.2 Scope
In scope: EN 16931-compliant invoice generation (XRechnung XML and ZUGFeRD hybrid PDF), schema/business-rule validation, receiving and parsing incoming structured e-invoices, 8-year immutable archival, transmission via email and the Peppol network, and a compliance dashboard tracking an organization's readiness against the phased mandate.

Out of scope: full ERP/general-ledger functionality (this tool integrates with, but does not replace, the invoicing/order data already modeled in the Financial & Inventory Management Platform), tax filing/submission to tax authorities, and B2C invoicing (out of mandate scope).

### 1.3 Regulatory Context
- **Legal basis**: Wachstumschancengesetz (Growth Opportunities Act), implemented via amendments to §14 of the German VAT Act (UStG).
- **Standard**: EN 16931 (European e-invoicing standard). Accepted German formats: **XRechnung** (XML, maintained by KoSIT, the German Peppol Authority) and **ZUGFeRD** (hybrid PDF/A-3 with embedded XML, jointly developed with France; EN 16931-compliant from v2.0.1 upward, excluding certain non-compliant profiles such as BASIC).
- **Timeline**:
  - Since **1 January 2025**: every business operating in Germany must be able to *receive* and process structured e-invoices.
  - From **1 January 2027**: businesses with annual turnover above **€800,000** must be able to *issue* EN 16931-compliant e-invoices for domestic B2B transactions.
  - From **1 January 2028**: the issuing obligation extends to *all* businesses.
  - B2G (business-to-government) e-invoicing has been mandatory since November 2020 via XRechnung, submitted through the ZRE/OZG-RE portal.
- **Model**: Germany uses a **decentralized** exchange model — there is no central government clearance platform for B2B invoices. Exchange happens directly between parties via email, Peppol, EDI, or a service provider platform.
- **Scope of the mandate**: domestic B2B transactions between businesses established in Germany. Excludes B2C, cross-border transactions, invoices ≤ €250, transport tickets, certain VAT-exempt supplies, and issuance by Kleinunternehmer (small-business VAT exemption holders).
- **Retention**: e-invoices must be archived for **8 years**, unaltered and machine-readable.
- **Validation semantics**: syntax violations render a document a *non-invoice* outright; business-rule violations (e.g., incorrect VAT breakdown) are treated as critical content failures. The issuing party retains ultimate responsibility for compliance even when using third-party validation tooling.

### 1.4 Definitions
- **EN 16931** – The European semantic data model standard for e-invoices.
- **XRechnung** – Germany's XML-only e-invoice format (UBL or CII syntax), EN 16931-compliant by construction.
- **ZUGFeRD** – Hybrid e-invoice format: a human-readable PDF/A-3 with a machine-readable XML embedded inside it.
- **KoSIT** – Coordination Office for IT Standards (Bremen); maintains XRechnung and acts as Germany's Peppol Authority.
- **Peppol** – Pan-European Public Procurement OnLine network, a standardized channel for exchanging structured business documents.
- **UStG** – Umsatzsteuergesetz, the German VAT Act.

### 1.5 Stakeholders
- Accounts payable / receivable staff (day-to-day invoice issuing and receiving)
- Finance/compliance officers (responsible for mandate adherence and audit readiness)
- ERP/integration developers (consume this tool's API from existing systems, e.g., the Financial & Inventory Management Platform)
- External trading partners (suppliers/customers exchanging invoices with the organization)

---

## 2. Overall Description

### 2.1 Product Perspective
A backend service, exposed via REST API, that plugs into an existing invoicing/order system (such as the Financial & Inventory Management Platform's `SalesOrder`/`Invoice` entities) to add EN 16931-compliant generation, validation, transmission, receipt, and archival of e-invoices. It is designed as a standalone module so it can also be integrated with other systems that need German e-invoicing compliance.

### 2.2 User Classes
| Role | Description | Access |
|---|---|---|
| AP/AR Clerk | Triggers invoice generation/sending; reviews received invoices | Operational, scoped to their organization's invoices |
| Compliance Officer | Monitors mandate readiness, reviews validation failures, manages archive | Read-heavy, reporting, exception handling |
| Integration Developer | Calls the service's API from upstream systems (ERP, the Financial Platform) | API/service-account access |
| External Trading Partner | Sends/receives invoices via Peppol or email (not a direct system user) | N/A — interacts only via the transmission layer |

### 2.3 Operating Environment
- Backend: Java 17+, Spring Boot 3.x
- Database: PostgreSQL 14+ (invoice records, validation results, archive metadata)
- Format libraries: an EN 16931/XRechnung XML generation & Schematron validation library; a ZUGFeRD hybrid PDF/A-3 generation library (embedding XML into a PDF/A-3 container)
- Transmission: SMTP for email exchange; a Peppol Access Point (self-hosted or via a certified service provider) for network-based exchange

### 2.4 Constraints
- Generated XRechnung/ZUGFeRD documents must pass EN 16931 Schematron business-rule validation before being considered "sendable" — the system shall not allow a syntactically or semantically invalid invoice to be transmitted as a compliant e-invoice.
- Archived invoices must be stored unaltered (immutable) for the full 8-year retention period, in their originally issued machine-readable form.
- ZUGFeRD output must use a compliant profile (v2.0.1+, EN 16931-conformant profile such as COMFORT or EXTENDED) — the BASIC profile alone is not sufficient for EN 16931 compliance and shall not be offered as a compliant option.
- The tool must not assume a central clearance authority; it must support direct, decentralized exchange.

### 2.5 Assumptions and Dependencies
- The organization already has a source system (e.g., the Financial & Inventory Management Platform) producing the underlying invoice/order data this tool converts into compliant e-invoice formats.
- A Peppol Access Point is available either self-hosted or via a third-party certified provider; this tool does not need to become a certified Access Point itself but must integrate with one.
- Legal/compliance interpretation of edge cases (e.g., Kleinunternehmer status, VAT exemptions) is provided by the organization; the tool enforces structural/technical compliance, not tax-law judgment calls.

---

## 3. Functional Requirements

### 3.1 Invoice Generation
- **FR-1**: The system shall generate an XRechnung XML document (UBL or CII syntax) from a source invoice record containing the data required by EN 16931 (parties, line items, tax breakdown, payment terms, totals).
- **FR-2**: The system shall generate a ZUGFeRD hybrid PDF/A-3 document, embedding an EN 16931-conformant XML payload (profile COMFORT or EXTENDED) inside a human-readable PDF representation of the invoice.
- **FR-3**: The system shall allow the caller (via API) to select the target format (XRechnung or ZUGFeRD) per invoice, based on trading-partner capability or preference.
- **FR-4**: The system shall support all mandatory EN 16931 core fields, including seller/buyer identification, invoice/tax currency, line-item breakdown, VAT category and rate per line, and payment means.
- **FR-5**: The system shall reject generation requests for source data missing a field mandatory under EN 16931, returning a clear, field-level error rather than producing an incomplete document.

### 3.2 Validation
- **FR-6**: The system shall validate every generated invoice against EN 16931 Schematron business rules before marking it as ready to send.
- **FR-7**: The system shall validate every received incoming e-invoice against the same rule set before accepting it into downstream accounts-payable processing.
- **FR-8**: The system shall distinguish, in its validation report, between **syntax errors** (document is not a valid e-invoice at all) and **business-rule violations** (structurally valid but content-non-compliant, e.g., VAT totals not reconciling to line items).
- **FR-9**: The system shall never auto-correct a source data error silently; validation failures shall be surfaced to a human for correction at the source.

### 3.3 Transmission
- **FR-10**: The system shall support sending a validated e-invoice as an email attachment to a specified recipient address.
- **FR-11**: The system shall support sending a validated e-invoice over the Peppol network via a configured Access Point, addressed by the recipient's Peppol participant ID.
- **FR-12**: The system shall record the transmission method, timestamp, and delivery status (sent / delivered / failed) for every outgoing invoice.
- **FR-13**: The system shall support configurable per-recipient transmission preference (email vs. Peppol), falling back to email when no Peppol capability is known for a recipient.

### 3.4 Receiving
- **FR-14**: The system shall accept incoming e-invoices via a monitored email inbox and via the Peppol Access Point.
- **FR-15**: The system shall parse an incoming XRechnung XML or ZUGFeRD hybrid PDF and extract structured invoice data for downstream processing (e.g., into the Financial Platform's accounts-payable workflow).
- **FR-16**: The system shall flag and quarantine (rather than silently discard) any incoming document that fails format detection or validation, with a reason code for manual review.

### 3.5 Archiving
- **FR-17**: The system shall archive every generated and received e-invoice, in its original machine-readable form, for a minimum retention period of 8 years.
- **FR-18**: Archived invoices shall be stored immutably — no update operation shall be permitted against an archived record, only append-only status/metadata annotations (e.g., "reviewed," "disputed").
- **FR-19**: The system shall support retrieval of any archived invoice by invoice number, trading partner, date range, or validation status, for audit purposes.

### 3.6 Compliance Dashboard
- **FR-20**: The system shall provide a dashboard showing the organization's current e-invoicing status: proportion of invoices sent/received in compliant structured format vs. non-compliant/legacy format.
- **FR-21**: The system shall alert Compliance Officers when the organization's issuance readiness is inconsistent with its applicable mandate deadline (e.g., approaching the €800,000-turnover issuance deadline without demonstrated capability).
- **FR-22**: The dashboard shall surface recent validation failures and quarantined incoming documents requiring attention.

### 3.7 Integration
- **FR-23**: The system shall expose a REST API allowing an upstream system (e.g., the Financial & Inventory Management Platform) to submit an invoice record for e-invoice generation and to query transmission/validation status.
- **FR-24**: The system shall expose a webhook or polling endpoint so an upstream system can be notified when a new compliant incoming invoice has been received and parsed.

---

## 4. Non-Functional Requirements

- **NFR-1 (Compliance Accuracy)**: The validation engine shall correctly apply the current EN 16931 Schematron rule set; the rule set version in use shall be tracked and upgradable independently of application code releases, given the standard evolves (e.g., ZUGFeRD 2.5 released May 2026 with updated code lists).
- **NFR-2 (Auditability)**: Every generation, validation, transmission, and archival action shall be logged with actor (system/user), timestamp, and outcome, sufficient to reconstruct a full compliance audit trail.
- **NFR-3 (Immutability)**: Archived invoice records shall be technically protected against modification or deletion within the retention period (e.g., write-once storage or database-level immutability constraints).
- **NFR-4 (Availability)**: The receiving pathway (email/Peppol inbound) shall be monitored continuously; downtime shall not result in silent loss of incoming invoices (queued/retried on recovery).
- **NFR-5 (Performance)**: Invoice generation and validation shall complete in under 3 seconds per document under normal load, to support synchronous use from an upstream invoicing workflow.
- **NFR-6 (Security)**: All transmission (email, Peppol, API) shall use encrypted channels (TLS). API access shall be authenticated (e.g., mutual TLS or OAuth2 client credentials for service-to-service calls).
- **NFR-7 (Correctness of Monetary Data)**: All monetary and tax calculations shall use fixed-point/decimal arithmetic; no floating-point rounding drift shall be introduced when converting source data into the EN 16931 XML representation.
- **NFR-8 (Format Currency)**: The system's ZUGFeRD/XRechnung generation logic shall be structured so that adopting a new minor version of either standard (as happened with ZUGFeRD 2.5) does not require a full application rewrite — format-specific logic shall be isolated behind a versioned adapter.
- **NFR-9 (Scalability)**: The system shall handle at least 1,000 invoices/day at launch, with a clear scaling path (horizontal service scaling) as issuance obligations phase in for more of the business by 2027–2028.

---

## 5. Data Model (Key Entities)

- **EInvoice**: id, sourceInvoiceId (FK to upstream system), format (XRechnung/ZUGFeRD), direction (outgoing/incoming), status (draft/validated/sent/received/rejected/archived), createdAt
- **ValidationResult**: id, eInvoiceId, ruleSetVersion, outcome (pass/syntaxError/businessRuleViolation), details (field-level errors), validatedAt
- **TransmissionRecord**: id, eInvoiceId, method (email/peppol), recipient, status (sent/delivered/failed), timestamp
- **ArchiveEntry**: id, eInvoiceId, storedDocument (immutable reference), retentionExpiresAt, annotations[] (append-only)
- **TradingPartner**: id, name, peppolParticipantId (nullable), preferredTransmissionMethod, vatId

---

## 6. External Interface Requirements

### 6.1 REST API
- `POST /api/v1/einvoices` — generate an e-invoice from source invoice data
- `GET /api/v1/einvoices/{id}/validation` — retrieve validation result
- `POST /api/v1/einvoices/{id}/send` — transmit via email or Peppol
- `GET /api/v1/einvoices/incoming` — list/query received invoices
- `GET /api/v1/einvoices/{id}/archive` — retrieve archived document and metadata
- Authenticated via OAuth2 client credentials (service-to-service) for integration with the Financial & Inventory Management Platform or other upstream systems.

### 6.2 Peppol Access Point
- Outbound/inbound integration with a certified Peppol Access Point (self-hosted AS4 endpoint or third-party provider API), addressed by Peppol participant ID.

### 6.3 Email
- SMTP for outbound invoice delivery as attachment; IMAP/monitored mailbox for inbound receipt.

### 6.4 File Formats
- XRechnung: XML, UBL or CII syntax, EN 16931-conformant
- ZUGFeRD: PDF/A-3 with embedded XML, profile COMFORT or EXTENDED (EN 16931-conformant), version ≥ 2.0.1

---

## 7. Architecture Overview

A Spring Boot service sits between the organization's existing invoicing data (e.g., the Financial & Inventory Management Platform's `SalesOrder`/`Invoice` records) and the outside world. On the outbound path, source invoice data is mapped into an EN 16931 semantic model, rendered into either XRechnung XML or a ZUGFeRD hybrid PDF, run through Schematron validation, and — only once valid — transmitted via email or Peppol, with the result archived immutably. On the inbound path, incoming documents are received via a monitored mailbox or Peppol Access Point, format-detected, parsed into structured data, validated, and either handed off to the upstream accounts-payable workflow or quarantined for manual review. A compliance dashboard aggregates validation and readiness metrics across both paths.

---

## 8. Acceptance Criteria (Sample)

- Given a source invoice with all EN 16931-mandatory fields populated, when generation is requested in XRechnung format, then a Schematron-valid XML document is produced and marked "validated."
- Given a source invoice missing a mandatory field (e.g., buyer VAT ID), when generation is requested, then the system returns a field-level error and does not produce a document marked as compliant.
- Given a validated e-invoice and a recipient with a known Peppol participant ID, when send is triggered, then the document is transmitted via Peppol and a delivery status is recorded.
- Given an incoming ZUGFeRD PDF that fails EN 16931 business-rule validation, then it is quarantined with a reason code rather than silently accepted into accounts-payable processing.
- Given an invoice has been archived, then no API operation is capable of modifying its stored content within the 8-year retention period — only append-only annotations are possible.

---

## 9. Open Questions / Assumptions to Confirm

- Will the organization self-host a Peppol Access Point, or integrate with a third-party certified provider — this affects the transmission-layer implementation significantly.
- Does the target organization currently sit below or above the €800,000 turnover threshold, which determines whether issuance capability is needed by 2027 or can wait until the 2028 deadline?
- Should the tool track Kleinunternehmer status per trading partner to correctly anticipate which counterparties are exempt from issuing (but not receiving) obligations?
- Is XRechnung the default/preferred output format, with ZUGFeRD offered only where a trading partner specifically needs a human-readable PDF alongside the structured data?
