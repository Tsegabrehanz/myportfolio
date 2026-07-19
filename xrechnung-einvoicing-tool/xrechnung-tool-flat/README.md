# XRechnung / ZUGFeRD E-Invoicing Compliance Tool

A Spring Boot service implementing the German B2B e-invoicing mandate
(EN 16931 / XRechnung / ZUGFeRD), built from the accompanying [SRS](./SRS.md).

## What's actually implemented (and verified working)

- **XRechnung XML generation** (`generation/XRechnungGenerator.java`) — a
  UBL 2.1 Invoice XML with seller/buyer identification, line items, VAT
  category/rate per line, a real computed VAT breakdown (`TaxTotal`/
  `TaxSubtotal` grouped by category+rate), and VAT-inclusive totals. JDK-native
  XML APIs only.
- **ZUGFeRD hybrid PDF generation** (`generation/ZugferdGenerator.java`) — a
  genuine, valid PDF (verified against Poppler's `pdfinfo`/`pdftoppm`/
  `pdfdetach` — an independent PDF implementation, not just this project's
  own code) with the XRechnung XML embedded as an attachment named
  `factur-x.xml`, built from raw PDF object syntax since no PDF library was
  reachable to integrate (see "How this was verified" below).
- **XRechnung XML parsing** (`parsing/XRechnungParser.java`) — reads a
  generated/received UBL invoice back into structured data (FR-15),
  namespace-aware, with XXE hardening (DOCTYPE declarations are rejected
  outright).
- **ZUGFeRD PDF parsing** (`parsing/ZugferdParser.java`) — extracts the
  embedded XML back out of a hybrid PDF. Verified byte-for-byte identical
  to Poppler's own `pdfdetach` extraction of the same file.
- **EN 16931 business-rule validation** (`validation/En16931Validator.java`)
  — a representative, honestly-scoped subset of the real rule set (see
  "What's simplified" below), distinguishing **syntax errors** from
  **business-rule violations** per FR-8.
- **Peppol transmission** (`service/HttpPeppolAccessPointClient.java`) — a
  real HTTP client (JDK-native `java.net.http`, no dependency) that POSTs to
  a configured Access Point base URL with proper auth headers, timeouts, and
  error handling. Verified against a genuine local HTTP server (JDK-native
  `com.sun.net.httpserver`) — see "How this was verified."
- **Full domain model, repository layer, service layer, and REST API**
  matching the SRS's data model and Section 6.1 API surface, including:
  - An `EInvoice` lifecycle (DRAFT → VALIDATED/REJECTED → SENT → ARCHIVED)
  - An `ArchiveEntryRepository` that **doesn't expose delete methods at
    all** (extends the bare `Repository` marker, not `JpaRepository`) —
    immutability enforced at the type level, not just by convention
  - Real email transmission via `JavaMailSender`
  - A compliance dashboard service (readiness rate, alerts)

## How this was actually verified

This project was built in a sandboxed environment **without access to Maven
Central** (confirmed: `repo.maven.apache.org` returns a network-level
`403 host_not_allowed`). The full Spring Boot application has **not** been
compiled or run end-to-end by me — `mvn compile`/`mvn test` were never
possible here.

What I could do, and did, for every piece of core logic — not just the
first pass, but each feature added afterward too:

1. Installed a JDK via `apt-get` (Ubuntu's mirror was reachable even though
   Maven Central wasn't) and compiled the dependency-free classes
   (`domain`, `generation`, `parsing`, `validation`, plus
   `HttpPeppolAccessPointClient` — none import Spring or JPA) directly with
   `javac`.
2. Wrote standalone verification harnesses (not JUnit — also on Maven
   Central) exercising realistic data, and ran them.
3. Where possible, checked results against **independent tooling already
   installed in the sandbox**, not just my own code's self-consistency:
   - The generated ZUGFeRD PDF was opened with **Poppler**'s `pdfinfo`,
     rendered with `pdftoppm`, and had its attachment listed and extracted
     with `pdfdetach` — a completely separate PDF implementation from
     anything in this codebase.
   - This project's own `ZugferdParser` extraction was then compared
     **byte-for-byte** against Poppler's extraction of the same file.
   - The Peppol client was tested against a real local HTTP server
     (`com.sun.net.httpserver`) with a real socket and real HTTP
     request/response cycle — not a mocked interface.

This process found and fixed **three real bugs**, not zero:

- **Namespace bug**: `XRechnungGenerator` was creating every `cac:`/`cbc:`
  element under the root `Invoice-2` namespace instead of the correct
  `CommonAggregateComponents-2`/`CommonBasicComponents-2` namespaces.
  Invisible to a substring-based test; caught the moment a namespace-aware
  parser (`XRechnungParser`, using XPath with a real namespace context) was
  written and a round-trip test run against it.
- **Missing VAT calculation**: the first version of `LegalMonetaryTotal`
  silently set `TaxInclusiveAmount` equal to the net total — no VAT was
  actually being calculated. Fixed with a proper per-category/rate VAT
  breakdown and real `TaxTotal`/`TaxSubtotal` elements.
- **Missing `/Length` on the embedded-file PDF stream**: the first version
  of `ZugferdGenerator` never declared `/Length` on the embedded XML
  stream's dictionary. Poppler's `pdfdetach` failed with "Bad 'Length'
  attribute in stream" and extracted a corrupted file (valid XML with
  trailing PDF syntax appended). Fixed, and a permanent guard was added to
  the PDF writer itself (`writeStreamObject` now throws if `/Length` is
  missing from any stream dictionary) so this bug class can't recur
  silently even in a future stream type.

Each fix was re-verified the same way before being considered done — see
the inline comments in the affected classes and their matching JUnit test
files (`XRechnungGeneratorTest`, `XRechnungParserTest`, `ZugferdGeneratorTest`,
`HttpPeppolAccessPointClientTest`) for the reproduced cases.

**The Spring wiring itself (controllers, JPA entities, `@Bean` definitions)
is written to the same standard but remains uncompiled by me** — cross-checked
by hand (every bean a constructor needs is registered; every service method
a controller calls actually exists — verified with grep, not just by eye),
but genuinely not built with a real classpath. Run `mvn test` yourself
before trusting it further.

## What's simplified (be upfront about this in interviews)

- **EN 16931 rule coverage**: `En16931Validator` implements a deliberately
  chosen subset (~15 rules) covering missing identification, VAT category/
  rate correctness, currency, and basic arithmetic. The official KoSIT
  Schematron rule set has 100+ rules — for production use, swap this for a
  real Schematron engine run against KoSIT's published rules (the
  **Mustangproject** library is the standard choice), which this sandbox
  couldn't fetch from Maven Central to integrate directly.
- **ZUGFeRD is PDF/A-3-*shaped*, not PDF/A-3-*conformant***. True ISO
  19005-3 conformance additionally needs an XMP metadata packet and an
  embedded ICC output-intent profile — neither implemented. A PDF/A-3
  validator (e.g. veraPDF) would flag this file as non-conformant even
  though it opens correctly and its attachment extracts cleanly. This is
  the single biggest gap between "genuinely working" (which it is) and
  "production-ready" (which it isn't yet) in this codebase.
- **`ZugferdParser`'s extraction is targeted, not general-purpose** — see
  its class javadoc. It handles this project's own generator output (and
  any PDF using an uncompressed embedded-file stream with a literal
  `/Length`), but not `FlateDecode`-compressed streams or indirect
  `/Length` references, both common in PDFs produced by real PDF
  libraries. A third-party ZUGFeRD PDF from an arbitrary sender is not
  guaranteed to parse — that's exactly the gap a real PDF library closes.
- **Peppol transmission is real but unexercised against the actual
  network** — see `HttpPeppolAccessPointClient`'s javadoc. It performs a
  correctly-structured HTTP call to whatever `einvoicing.peppol.base-url`
  points at; it cannot itself become a certified Access Point, which is an
  organizational/legal step, not a coding one.

## Project layout

```
src/main/java/com/tsegabrehan/einvoicing/
  domain/        entities + the SourceInvoice input model
  generation/    XRechnungGenerator, ZugferdGenerator (pure Java, no deps)
  parsing/       XRechnungParser, ZugferdParser (pure Java, no deps)
  validation/    En16931Validator (pure Java, no deps)
  repository/    Spring Data interfaces (note ArchiveEntryRepository)
  service/       orchestration, transmission (incl. HttpPeppolAccessPointClient), archiving, dashboard
  api/           REST controller + DTOs
  config/        bean wiring for the pure-Java classes + Peppol client selection
src/test/java/   JUnit 5 tests — every one mirrors a check already run standalone in the sandbox
```

## Running it (once you have normal internet access)

```bash
mvn spring-boot:run
```

Starts on `:8080` with an in-memory H2 database (see `application.yml`).
For PostgreSQL, run with `-Dspring-boot.run.profiles=postgres` and set the
`DB_HOST`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD` environment variables.

```bash
mvn test
```

### Example: generate an invoice

```bash
# XRechnung (default)
curl -X POST localhost:8080/api/v1/einvoices \
  -H "Content-Type: application/json" \
  -d '{
    "invoiceNumber": "INV-2026-0001",
    "issueDate": "2026-07-15",
    "currencyCode": "EUR",
    "seller": {"name":"Tsegabrehan Software GmbH","vatId":"DE123456789","countryCode":"DE"},
    "buyer": {"name":"Musterkunde AG","vatId":"DE987654321","countryCode":"DE"},
    "lines": [{"lineId":"1","description":"Consulting","quantity":10,"unitPrice":150.00,"vatCategoryCode":"S","vatRatePercent":19.00}]
  }'

# ZUGFeRD hybrid PDF
curl -X POST "localhost:8080/api/v1/einvoices?format=ZUGFERD" \
  -H "Content-Type: application/json" \
  -d '{ ... same body ... }'
```

### Example: enable Peppol transmission

```bash
export EINVOICING_PEPPOL_BASE_URL=https://your-access-point-provider.example.com/api
export EINVOICING_PEPPOL_API_KEY=your-real-api-key
mvn spring-boot:run
```

Without these set, Peppol sends fail fast with a clear
`UnsupportedOperationException` rather than silently pretending to succeed.

### Package a runnable jar

```bash
mvn clean package
java -jar target/xrechnung-einvoicing-tool-0.1.0.jar
```

## Relationship to the rest of the portfolio

Designed to sit behind the **Financial & Inventory Management Platform**
project's `SalesOrder`/`Invoice` records — that system produces the business
data, this service turns it into a compliant e-invoice.
