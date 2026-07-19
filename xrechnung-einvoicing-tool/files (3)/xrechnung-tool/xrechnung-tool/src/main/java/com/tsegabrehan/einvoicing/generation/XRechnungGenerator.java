package com.tsegabrehan.einvoicing.generation;

import com.tsegabrehan.einvoicing.domain.Party;
import com.tsegabrehan.einvoicing.domain.SourceInvoice;
import com.tsegabrehan.einvoicing.domain.SourceInvoiceLine;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Generates an EN 16931 / XRechnung-shaped invoice XML (UBL 2.1 Invoice
 * syntax) from a {@link SourceInvoice} (SRS FR-1, FR-4, FR-5).
 * <p>
 * <b>Scope note:</b> this produces a structurally correct UBL Invoice
 * document carrying the EN 16931 core fields (BT-1 invoice number, BT-2
 * issue date, BT-5 currency, seller/buyer identification, line items with
 * VAT category/rate, and computed totals). It does not reproduce KoSIT's
 * full XRechnung XSD/Schematron rule set byte-for-byte — see the README's
 * "What's simplified" section for what a production implementation would
 * add (e.g. via the Mustangproject library, which this sandbox could not
 * fetch from Maven Central to integrate directly).
 * <p>
 * Uses only JDK-provided XML APIs ({@code javax.xml.parsers},
 * {@code javax.xml.transform}) — no third-party XML library dependency.
 */
public class XRechnungGenerator {

    private static final String UBL_INVOICE_NS = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    private static final String CBC_NS = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String CAC_NS = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";

    /**
     * Validates that the mandatory fields for generation are present, per FR-5.
     * Returns a list of human-readable field errors; empty list means the
     * source data has the minimum shape required to attempt generation.
     * (This is a pre-generation structural check, distinct from the full
     * EN 16931 business-rule validation performed afterwards by
     * {@link com.tsegabrehan.einvoicing.validation.En16931Validator}.)
     */
    public List<String> checkRequiredFields(SourceInvoice invoice) {
        var errors = new java.util.ArrayList<String>();
        if (invoice == null) {
            errors.add("source invoice is required");
            return errors;
        }
        if (isBlank(invoice.invoiceNumber())) errors.add("invoiceNumber (BT-1) is required");
        if (invoice.issueDate() == null) errors.add("issueDate (BT-2) is required");
        if (isBlank(invoice.currencyCode())) errors.add("currencyCode (BT-5) is required");
        errors.addAll(checkParty("seller", invoice.seller()));
        errors.addAll(checkParty("buyer", invoice.buyer()));
        if (invoice.lines() == null || invoice.lines().isEmpty()) {
            errors.add("at least one invoice line is required");
        } else {
            for (int i = 0; i < invoice.lines().size(); i++) {
                SourceInvoiceLine line = invoice.lines().get(i);
                String prefix = "line[" + i + "] ";
                if (isBlank(line.lineId())) errors.add(prefix + "lineId is required");
                if (isBlank(line.description())) errors.add(prefix + "description is required");
                if (line.quantity() == null) errors.add(prefix + "quantity is required");
                if (line.unitPrice() == null) errors.add(prefix + "unitPrice is required");
                if (isBlank(line.vatCategoryCode())) errors.add(prefix + "vatCategoryCode is required");
            }
        }
        return errors;
    }

    private List<String> checkParty(String label, Party party) {
        var errors = new java.util.ArrayList<String>();
        if (party == null) {
            errors.add(label + " is required");
            return errors;
        }
        if (isBlank(party.name())) errors.add(label + ".name is required");
        if (isBlank(party.vatId())) errors.add(label + ".vatId is required");
        if (isBlank(party.countryCode())) errors.add(label + ".countryCode is required");
        return errors;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Generates the XRechnung XML document as UTF-8 bytes.
     *
     * @throws IllegalArgumentException if required fields are missing (FR-5)
     */
    public byte[] generate(SourceInvoice invoice) {
        List<String> fieldErrors = checkRequiredFields(invoice);
        if (!fieldErrors.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot generate XRechnung XML, missing required EN 16931 fields: " + String.join("; ", fieldErrors));
        }

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder builder = dbf.newDocumentBuilder();
            Document doc = builder.newDocument();

            Element root = doc.createElementNS(UBL_INVOICE_NS, "Invoice");
            root.setAttribute("xmlns:cbc", CBC_NS);
            root.setAttribute("xmlns:cac", CAC_NS);
            doc.appendChild(root);

            appendCbc(doc, root, "CustomizationID",
                    "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0");
            appendCbc(doc, root, "ID", invoice.invoiceNumber());
            appendCbc(doc, root, "IssueDate", invoice.issueDate().toString());
            appendCbc(doc, root, "InvoiceTypeCode", "380"); // 380 = Commercial invoice
            appendCbc(doc, root, "DocumentCurrencyCode", invoice.currencyCode());

            root.appendChild(buildParty(doc, "AccountingSupplierParty", invoice.seller()));
            root.appendChild(buildParty(doc, "AccountingCustomerParty", invoice.buyer()));

            BigDecimal lineTotal = BigDecimal.ZERO;
            int index = 1;
            for (SourceInvoiceLine line : invoice.lines()) {
                root.appendChild(buildInvoiceLine(doc, line, invoice.currencyCode(), index++));
                lineTotal = lineTotal.add(line.netAmount());
            }
            lineTotal = lineTotal.setScale(2, RoundingMode.HALF_UP);

            var vatBreakdown = computeVatBreakdown(invoice.lines());
            BigDecimal vatTotal = vatBreakdown.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            root.appendChild(buildTaxTotal(doc, invoice.currencyCode(), vatBreakdown, vatTotal));
            root.appendChild(buildLegalMonetaryTotal(doc, invoice.currencyCode(), lineTotal, vatTotal));

            return serialize(doc);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate XRechnung XML for invoice "
                    + invoice.invoiceNumber(), e);
        }
    }

    private Element buildParty(Document doc, String wrapperName, Party party) {
        Element wrapper = doc.createElementNS(UBL_INVOICE_NS, "cac:" + wrapperName);
        Element partyEl = doc.createElementNS(UBL_INVOICE_NS, "cac:Party");

        Element vatSchemeId = doc.createElementNS(UBL_INVOICE_NS, "cac:PartyTaxScheme");
        appendCbc(doc, vatSchemeId, "CompanyID", party.vatId());
        Element taxScheme = doc.createElementNS(UBL_INVOICE_NS, "cac:TaxScheme");
        appendCbc(doc, taxScheme, "ID", "VAT");
        vatSchemeId.appendChild(taxScheme);
        partyEl.appendChild(vatSchemeId);

        Element legalEntity = doc.createElementNS(UBL_INVOICE_NS, "cac:PartyLegalEntity");
        appendCbc(doc, legalEntity, "RegistrationName", party.name());
        partyEl.appendChild(legalEntity);

        Element address = doc.createElementNS(UBL_INVOICE_NS, "cac:PostalAddress");
        appendCbc(doc, address, "StreetName", Objects.requireNonNullElse(party.addressLine(), ""));
        appendCbc(doc, address, "CityName", Objects.requireNonNullElse(party.city(), ""));
        appendCbc(doc, address, "PostalZone", Objects.requireNonNullElse(party.postalCode(), ""));
        Element country = doc.createElementNS(UBL_INVOICE_NS, "cac:Country");
        appendCbc(doc, country, "IdentificationCode", party.countryCode());
        address.appendChild(country);
        partyEl.appendChild(address);

        wrapper.appendChild(partyEl);
        return wrapper;
    }

    private Element buildInvoiceLine(Document doc, SourceInvoiceLine line, String currency, int positionFallback) {
        Element lineEl = doc.createElementNS(UBL_INVOICE_NS, "cac:InvoiceLine");
        appendCbc(doc, lineEl, "ID",
                line.lineId() != null ? line.lineId() : String.valueOf(positionFallback));

        Element quantityEl = doc.createElementNS(UBL_INVOICE_NS, "cbc:InvoicedQuantity");
        quantityEl.setTextContent(line.quantity().toPlainString());
        lineEl.appendChild(quantityEl);

        Element lineExtensionAmount = doc.createElementNS(UBL_INVOICE_NS, "cbc:LineExtensionAmount");
        lineExtensionAmount.setAttribute("currencyID", currency);
        lineExtensionAmount.setTextContent(line.netAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
        lineEl.appendChild(lineExtensionAmount);

        Element item = doc.createElementNS(UBL_INVOICE_NS, "cac:Item");
        appendCbc(doc, item, "Name", line.description());
        Element classifiedTaxCategory = doc.createElementNS(UBL_INVOICE_NS, "cac:ClassifiedTaxCategory");
        appendCbc(doc, classifiedTaxCategory, "ID", line.vatCategoryCode());
        if (line.vatRatePercent() != null) {
            appendCbc(doc, classifiedTaxCategory, "Percent", line.vatRatePercent().toPlainString());
        }
        Element taxScheme = doc.createElementNS(UBL_INVOICE_NS, "cac:TaxScheme");
        appendCbc(doc, taxScheme, "ID", "VAT");
        classifiedTaxCategory.appendChild(taxScheme);
        item.appendChild(classifiedTaxCategory);
        lineEl.appendChild(item);

        Element price = doc.createElementNS(UBL_INVOICE_NS, "cac:Price");
        Element priceAmount = doc.createElementNS(UBL_INVOICE_NS, "cbc:PriceAmount");
        priceAmount.setAttribute("currencyID", currency);
        priceAmount.setTextContent(line.unitPrice().setScale(2, RoundingMode.HALF_UP).toPlainString());
        price.appendChild(priceAmount);
        lineEl.appendChild(price);

        return lineEl;
    }

    private Element buildLegalMonetaryTotal(Document doc, String currency, BigDecimal lineTotal, BigDecimal vatTotal) {
        Element total = doc.createElementNS(UBL_INVOICE_NS, "cac:LegalMonetaryTotal");
        BigDecimal taxInclusive = lineTotal.add(vatTotal).setScale(2, RoundingMode.HALF_UP);
        appendAmount(doc, total, "LineExtensionAmount", currency, lineTotal);
        appendAmount(doc, total, "TaxExclusiveAmount", currency, lineTotal);
        appendAmount(doc, total, "TaxInclusiveAmount", currency, taxInclusive);
        appendAmount(doc, total, "PayableAmount", currency, taxInclusive);
        return total;
    }

    /**
     * Groups line net amounts by (VAT category, rate) and computes the VAT
     * amount per group — EN 16931 requires the tax breakdown at this
     * granularity (BG-23 VAT Breakdown), not just a single blended total.
     */
    private java.util.Map<String, BigDecimal> computeVatBreakdown(List<SourceInvoiceLine> lines) {
        // key = "<categoryCode>|<ratePercent>", value = accumulated VAT amount for that group
        var netByGroup = new java.util.LinkedHashMap<String, BigDecimal>();
        var rateByGroup = new java.util.LinkedHashMap<String, BigDecimal>();
        for (SourceInvoiceLine line : lines) {
            BigDecimal rate = line.vatRatePercent() != null ? line.vatRatePercent() : BigDecimal.ZERO;
            String key = line.vatCategoryCode() + "|" + rate.toPlainString();
            netByGroup.merge(key, line.netAmount(), BigDecimal::add);
            rateByGroup.putIfAbsent(key, rate);
        }
        var vatByGroup = new java.util.LinkedHashMap<String, BigDecimal>();
        for (var entry : netByGroup.entrySet()) {
            BigDecimal rate = rateByGroup.get(entry.getKey());
            BigDecimal vat = entry.getValue()
                    .multiply(rate)
                    .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                    .setScale(2, RoundingMode.HALF_UP);
            vatByGroup.put(entry.getKey(), vat);
        }
        return vatByGroup;
    }

    private Element buildTaxTotal(Document doc, String currency,
                                   java.util.Map<String, BigDecimal> vatBreakdown, BigDecimal vatTotal) {
        Element taxTotal = doc.createElementNS(UBL_INVOICE_NS, "cac:TaxTotal");
        appendAmount(doc, taxTotal, "TaxAmount", currency, vatTotal);

        for (var entry : vatBreakdown.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            String categoryCode = parts[0];
            BigDecimal rate = new BigDecimal(parts[1]);
            BigDecimal vatAmount = entry.getValue();

            Element subtotal = doc.createElementNS(UBL_INVOICE_NS, "cac:TaxSubtotal");
            appendAmount(doc, subtotal, "TaxAmount", currency, vatAmount);
            Element category = doc.createElementNS(UBL_INVOICE_NS, "cac:TaxCategory");
            appendCbc(doc, category, "ID", categoryCode);
            appendCbc(doc, category, "Percent", rate.toPlainString());
            Element scheme = doc.createElementNS(UBL_INVOICE_NS, "cac:TaxScheme");
            appendCbc(doc, scheme, "ID", "VAT");
            category.appendChild(scheme);
            subtotal.appendChild(category);
            taxTotal.appendChild(subtotal);
        }
        return taxTotal;
    }

    private void appendAmount(Document doc, Element parent, String name, String currency, BigDecimal amount) {
        Element el = doc.createElementNS(UBL_INVOICE_NS, "cbc:" + name);
        el.setAttribute("currencyID", currency);
        el.setTextContent(amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
        parent.appendChild(el);
    }

    private void appendCbc(Document doc, Element parent, String name, String value) {
        Element el = doc.createElementNS(UBL_INVOICE_NS, "cbc:" + name);
        el.setTextContent(value);
        parent.appendChild(el);
    }

    private byte[] serialize(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(out));
        return out.toByteArray();
    }
}
