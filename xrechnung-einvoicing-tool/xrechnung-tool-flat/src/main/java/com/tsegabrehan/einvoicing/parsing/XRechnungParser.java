package com.tsegabrehan.einvoicing.parsing;

import com.tsegabrehan.einvoicing.domain.Party;
import com.tsegabrehan.einvoicing.domain.SourceInvoice;
import com.tsegabrehan.einvoicing.domain.SourceInvoiceLine;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Parses an XRechnung (UBL Invoice) XML document back into a
 * {@link SourceInvoice} — the counterpart to
 * {@link com.tsegabrehan.einvoicing.generation.XRechnungGenerator} (FR-15).
 * <p>
 * <b>Scope note:</b> parses the fields this project's own generator
 * produces (and any UBL Invoice using the same element shapes — most
 * XRechnung-conformant senders will match this, since the shape is
 * standard). It does not attempt to handle every legal syntactic
 * variation the UBL/CII schemas allow (e.g. it expects one
 * {@code cac:ClassifiedTaxCategory} per line rather than exploring every
 * allowed nesting alternative). Round-trips correctly against
 * {@code XRechnungGenerator} output — see
 * {@code XRechnungRoundTripTest} — which is the case this service
 * actually needs to handle for FR-15 (validating/re-processing e-invoices
 * this organization or its immediate trading partners produce).
 * <p>
 * Uses only JDK-native XML APIs (DOM + XPath), no external dependency.
 */
public class XRechnungParser {

    private static final String NS = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";

    public static class ParseException extends RuntimeException {
        public ParseException(String message) {
            super(message);
        }
        public ParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public SourceInvoice parse(byte[] xmlBytes) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            // Harden against XXE: no external entity/DTD resolution for untrusted incoming documents.
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            DocumentBuilder builder = dbf.newDocumentBuilder();
            org.w3c.dom.Document doc = builder.parse(new ByteArrayInputStream(xmlBytes));

            XPath xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(new UblNamespaceContext());

            String invoiceNumber = text(xpath, doc, "//cbc:ID");
            String issueDateStr = text(xpath, doc, "//cbc:IssueDate");
            String currencyCode = text(xpath, doc, "//cbc:DocumentCurrencyCode");

            Party seller = parseParty(xpath, doc, "//cac:AccountingSupplierParty");
            Party buyer = parseParty(xpath, doc, "//cac:AccountingCustomerParty");

            List<SourceInvoiceLine> lines = parseLines(xpath, doc);

            if (invoiceNumber == null || invoiceNumber.isBlank()) {
                throw new ParseException("Document has no cbc:ID (invoice number) — not a recognisable XRechnung invoice.");
            }
            LocalDate issueDate;
            try {
                issueDate = LocalDate.parse(issueDateStr);
            } catch (Exception e) {
                throw new ParseException("Could not parse IssueDate '" + issueDateStr + "' as an ISO date.", e);
            }

            return new SourceInvoice(invoiceNumber, issueDate, currencyCode, seller, buyer, lines, null);
        } catch (ParseException e) {
            throw e;
        } catch (Exception e) {
            throw new ParseException("Failed to parse document as XRechnung XML: " + e.getMessage(), e);
        }
    }

    private Party parseParty(XPath xpath, Node root, String wrapperPath) throws Exception {
        Node wrapper = (Node) xpath.evaluate(wrapperPath, root, XPathConstants.NODE);
        if (wrapper == null) {
            return null;
        }
        String name = text(xpath, wrapper, ".//cac:PartyLegalEntity/cbc:RegistrationName");
        String vatId = text(xpath, wrapper, ".//cac:PartyTaxScheme/cbc:CompanyID");
        String street = text(xpath, wrapper, ".//cac:PostalAddress/cbc:StreetName");
        String city = text(xpath, wrapper, ".//cac:PostalAddress/cbc:CityName");
        String zone = text(xpath, wrapper, ".//cac:PostalAddress/cbc:PostalZone");
        String country = text(xpath, wrapper, ".//cac:PostalAddress/cac:Country/cbc:IdentificationCode");
        return new Party(name, vatId, street, zone, city, country);
    }

    private List<SourceInvoiceLine> parseLines(XPath xpath, Node root) throws Exception {
        List<SourceInvoiceLine> lines = new ArrayList<>();
        NodeList lineNodes = (NodeList) xpath.evaluate("//cac:InvoiceLine", root, XPathConstants.NODESET);
        for (int i = 0; i < lineNodes.getLength(); i++) {
            Node lineNode = lineNodes.item(i);
            String lineId = text(xpath, lineNode, "./cbc:ID");
            String quantityStr = text(xpath, lineNode, "./cbc:InvoicedQuantity");
            String description = text(xpath, lineNode, "./cac:Item/cbc:Name");
            String vatCategory = text(xpath, lineNode, "./cac:Item/cac:ClassifiedTaxCategory/cbc:ID");
            String vatRateStr = text(xpath, lineNode, "./cac:Item/cac:ClassifiedTaxCategory/cbc:Percent");
            String unitPriceStr = text(xpath, lineNode, "./cac:Price/cbc:PriceAmount");

            BigDecimal quantity = parseDecimal(quantityStr);
            BigDecimal unitPrice = parseDecimal(unitPriceStr);
            BigDecimal vatRate = vatRateStr == null || vatRateStr.isBlank() ? null : parseDecimal(vatRateStr);

            lines.add(new SourceInvoiceLine(lineId, description, quantity, unitPrice, vatCategory, vatRate));
        }
        return lines;
    }

    private BigDecimal parseDecimal(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            throw new ParseException("Could not parse numeric value '" + s + "'.", e);
        }
    }

    private String text(XPath xpath, Node context, String path) throws Exception {
        Node node = (Node) xpath.evaluate(path, context, XPathConstants.NODE);
        return node == null ? null : node.getTextContent();
    }

    /** Maps the cbc/cac prefixes used in the generated XML to their real namespace URIs. */
    private static class UblNamespaceContext implements javax.xml.namespace.NamespaceContext {
        @Override
        public String getNamespaceURI(String prefix) {
            return switch (prefix) {
                case "cbc" -> "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
                case "cac" -> "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
                default -> NS;
            };
        }

        @Override
        public String getPrefix(String namespaceURI) {
            return null;
        }

        @Override
        public java.util.Iterator<String> getPrefixes(String namespaceURI) {
            return null;
        }
    }
}
