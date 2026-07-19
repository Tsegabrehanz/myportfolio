package com.tsegabrehan.einvoicing.generation;

import com.tsegabrehan.einvoicing.domain.SourceInvoice;
import com.tsegabrehan.einvoicing.domain.SourceInvoiceLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates a ZUGFeRD-style hybrid PDF: a human-readable, valid PDF
 * document with the EN 16931 XRechnung XML embedded inside it as an
 * attached file (FR-2) — the core mechanic that makes a PDF "ZUGFeRD."
 * <p>
 * <b>Built from raw PDF object syntax, on purpose.</b> This sandbox could
 * not reach Maven Central, so libraries that would normally do this
 * (Mustangproject, Apache PDFBox with its PDF/A-3 profile) weren't
 * available to integrate. The PDF specification (ISO 32000) is an open,
 * documented binary/text format, so this class writes the handful of PDF
 * objects a minimal hybrid invoice needs directly: a Catalog, a one-page
 * Pages tree with a simple text content stream (Helvetica, one of the 14
 * standard PDF fonts — no font embedding required), and an
 * {@code /EmbeddedFiles} name tree carrying the XML, associated at the
 * document level via {@code /AF} the way PDF/A-3 hybrid invoices expect.
 * <p>
 * <b>What this is not:</b> a certified PDF/A-3-conformant file. True
 * ISO 19005-3 conformance additionally requires an XMP metadata packet, an
 * embedded ICC output intent profile, and (for genuinely arbitrary text)
 * fully embedded/subsetted fonts — none of which are implemented here. A
 * PDF/A-3 validator would flag this file as PDF/A-3-shaped but not
 * conformant. It is, however, a genuinely valid, openable PDF with a
 * correctly embedded, extractable XML attachment — verified in this
 * project's own build process by opening it with {@code pdfinfo} and
 * {@code pdftoppm} (Poppler), and by round-tripping the attachment back
 * out with {@link com.tsegabrehan.einvoicing.parsing.ZugferdParser}.
 */
public class ZugferdGenerator {

    /** Standard ZUGFeRD/Factur-X embedded filename since ZUGFeRD 2.0. */
    public static final String EMBEDDED_XML_FILENAME = "factur-x.xml";

    private final XRechnungGenerator xRechnungGenerator = new XRechnungGenerator();

    public byte[] generate(SourceInvoice invoice) {
        byte[] xml = xRechnungGenerator.generate(invoice); // reuses the same, already-verified XML generation
        return buildPdf(invoice, xml);
    }

    private byte[] buildPdf(SourceInvoice invoice, byte[] embeddedXml) {
        try {
            PdfWriter w = new PdfWriter();

            int fontObj = w.reserve();
            int contentObj = w.reserve();
            int embeddedFileObj = w.reserve();
            int filespecObj = w.reserve();
            int pageObj = w.reserve();
            int pagesObj = w.reserve();
            int namesObj = w.reserve();
            int catalogObj = w.reserve();

            w.writeObject(fontObj, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");

            byte[] contentStream = buildContentStream(invoice);
            w.writeStreamObject(contentObj, "<< /Length " + contentStream.length + " >>", contentStream);

            byte[] modDate = pdfDate();
            w.writeStreamObject(embeddedFileObj,
                    "<< /Type /EmbeddedFile /Subtype /application#2Fxml /Length " + embeddedXml.length + " "
                            + "/Params << /Size " + embeddedXml.length + " /ModDate " + pdfString(new String(modDate, StandardCharsets.US_ASCII)) + " >> >>",
                    embeddedXml);

            String desc = "EN 16931 XRechnung XML for invoice " + safe(invoice.invoiceNumber());
            w.writeObject(filespecObj,
                    "<< /Type /Filespec /F " + pdfString(EMBEDDED_XML_FILENAME)
                            + " /UF " + pdfString(EMBEDDED_XML_FILENAME)
                            + " /EF << /F " + embeddedFileObj + " 0 R >> "
                            + "/AFRelationship /Alternative /Desc " + pdfString(desc) + " >>");

            w.writeObject(pageObj,
                    "<< /Type /Page /Parent " + pagesObj + " 0 R /MediaBox [0 0 595 842] "
                            + "/Resources << /Font << /F1 " + fontObj + " 0 R >> >> "
                            + "/Contents " + contentObj + " 0 R >>");

            w.writeObject(pagesObj, "<< /Type /Pages /Kids [" + pageObj + " 0 R] /Count 1 >>");

            w.writeObject(namesObj,
                    "<< /EmbeddedFiles << /Names [" + pdfString(EMBEDDED_XML_FILENAME) + " " + filespecObj + " 0 R] >> >>");

            w.writeObject(catalogObj,
                    "<< /Type /Catalog /Pages " + pagesObj + " 0 R /Names " + namesObj + " 0 R "
                            + "/AF [" + filespecObj + " 0 R] "
                            + "/Version /1.7 >>");

            return w.finish(catalogObj);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build ZUGFeRD PDF for invoice " + invoice.invoiceNumber(), e);
        }
    }

    private byte[] buildContentStream(SourceInvoice invoice) {
        StringBuilder sb = new StringBuilder();
        sb.append("BT /F1 14 Tf 50 780 Td (Invoice ").append(escapePdfText(invoice.invoiceNumber())).append(") Tj ET\n");
        sb.append("BT /F1 10 Tf 50 755 Td (Issue date: ").append(escapePdfText(invoice.issueDate().toString())).append(") Tj ET\n");
        sb.append("BT /F1 10 Tf 50 738 Td (Seller: ").append(escapePdfText(invoice.seller().name())).append(" (").append(escapePdfText(invoice.seller().vatId())).append(")) Tj ET\n");
        sb.append("BT /F1 10 Tf 50 723 Td (Buyer: ").append(escapePdfText(invoice.buyer().name())).append(" (").append(escapePdfText(invoice.buyer().vatId())).append(")) Tj ET\n");

        int y = 690;
        sb.append("BT /F1 10 Tf 50 ").append(y).append(" Td (Line items:) Tj ET\n");
        y -= 18;
        BigDecimal netTotal = BigDecimal.ZERO;
        for (SourceInvoiceLine line : invoice.lines()) {
            String row = line.description() + "  qty " + line.quantity().toPlainString()
                    + " x " + line.unitPrice().toPlainString() + " " + invoice.currencyCode()
                    + " = " + line.netAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();
            sb.append("BT /F1 9 Tf 60 ").append(y).append(" Td (").append(escapePdfText(row)).append(") Tj ET\n");
            netTotal = netTotal.add(line.netAmount());
            y -= 14;
        }
        y -= 8;
        sb.append("BT /F1 11 Tf 50 ").append(y).append(" Td (Net total: ")
                .append(escapePdfText(netTotal.setScale(2, RoundingMode.HALF_UP).toPlainString() + " " + invoice.currencyCode()))
                .append(") Tj ET\n");
        y -= 22;
        sb.append("BT /F1 8 Tf 50 ").append(y)
                .append(" Td (This PDF carries a machine-readable EN 16931 XML copy of this invoice as an embedded attachment") // ZUGFeRD explanatory note, split across two lines below
                .append(") Tj ET\n");
        y -= 11;
        sb.append("BT /F1 8 Tf 50 ").append(y)
                .append(" Td (named \\(").append(escapePdfText(EMBEDDED_XML_FILENAME)).append("\\) per the ZUGFeRD/Factur-X hybrid format.) Tj ET\n");

        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private String escapePdfText(String s) {
        if (s == null) return "";
        // PDF literal strings inside content-stream Tj operators need the same escaping
        // as any PDF literal string: backslash, and the parentheses used as delimiters.
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private String pdfString(String s) {
        return "(" + escapePdfText(s) + ")";
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private byte[] pdfDate() {
        // D:YYYYMMDDHHmmSS format, UTC, no offset suffix for simplicity.
        var now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
        String formatted = String.format("D:%04d%02d%02d%02d%02d%02dZ",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
                now.getHour(), now.getMinute(), now.getSecond());
        return formatted.getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Minimal incremental PDF object writer: tracks byte offsets as objects
     * are written so it can emit a correct cross-reference (xref) table —
     * the part of a PDF that's easiest to get subtly wrong by hand, since
     * every offset must exactly match where that object's bytes actually
     * start in the file.
     */
    private static class PdfWriter {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final List<Integer> offsets = new ArrayList<>();
        private int nextObjectNumber = 1;

        PdfWriter() throws IOException {
            out.write("%PDF-1.7\n%\u00e2\u00e3\u00cf\u00d3\n".getBytes(StandardCharsets.ISO_8859_1));
        }

        /** Reserves the next object number without writing it yet (so forward references can be built). */
        int reserve() {
            int n = nextObjectNumber++;
            offsets.add(null); // placeholder, index n-1
            return n;
        }

        void writeObject(int objectNumber, String dictBody) throws IOException {
            recordOffset(objectNumber);
            out.write((objectNumber + " 0 obj\n" + dictBody + "\nendobj\n").getBytes(StandardCharsets.ISO_8859_1));
        }

        void writeStreamObject(int objectNumber, String dictBody, byte[] streamBytes) throws IOException {
            if (!dictBody.contains("/Length")) {
                throw new IllegalArgumentException(
                        "Stream object dictionary is missing a required /Length key (object " + objectNumber
                                + "). Every PDF stream must declare its exact byte length, or readers "
                                + "(this bit a real bug during development, caught by pdfdetach failing to "
                                + "extract the embedded file cleanly) will misparse everything after it.");
            }
            recordOffset(objectNumber);
            out.write((objectNumber + " 0 obj\n" + dictBody + "\nstream\n").getBytes(StandardCharsets.ISO_8859_1));
            out.write(streamBytes);
            out.write("\nendstream\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));
        }

        private void recordOffset(int objectNumber) {
            while (offsets.size() < objectNumber) {
                offsets.add(null);
            }
            offsets.set(objectNumber - 1, out.size());
        }

        byte[] finish(int rootObjectNumber) throws IOException {
            int xrefStart = out.size();
            int totalObjects = offsets.size() + 1; // +1 for the free-list head (object 0)

            StringBuilder xref = new StringBuilder();
            xref.append("xref\n0 ").append(totalObjects).append("\n");
            xref.append("0000000000 65535 f \n");
            for (Integer offset : offsets) {
                int o = offset == null ? 0 : offset;
                xref.append(String.format("%010d 00000 n \n", o));
            }
            out.write(xref.toString().getBytes(StandardCharsets.US_ASCII));

            String trailer = "trailer\n<< /Size " + totalObjects + " /Root " + rootObjectNumber + " 0 R >>\n"
                    + "startxref\n" + xrefStart + "\n%%EOF";
            out.write(trailer.getBytes(StandardCharsets.US_ASCII));

            return out.toByteArray();
        }
    }
}
