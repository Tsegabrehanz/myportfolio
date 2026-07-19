package com.tsegabrehan.einvoicing.parsing;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the embedded XRechnung XML from a ZUGFeRD hybrid PDF (FR-15),
 * the counterpart to {@link com.tsegabrehan.einvoicing.generation.ZugferdGenerator}.
 * <p>
 * <b>Scope note (read before relying on this for arbitrary third-party
 * PDFs):</b> this is a targeted extractor, not a general-purpose PDF
 * parser. It scans the raw PDF bytes for an {@code /EmbeddedFile} stream
 * object, reads its declared {@code /Length}, and returns exactly that
 * many bytes from the stream body. That's sufficient for:
 * <ul>
 *   <li>PDFs this project's own {@code ZugferdGenerator} produces
 *       (verified — see {@code ZugferdRoundTripTest})</li>
 *   <li>Any PDF using an uncompressed embedded-file stream with a direct
 *       (non-indirect) {@code /Length} — which covers a meaningful chunk
 *       of real ZUGFeRD producers, though by no means all of them</li>
 * </ul>
 * It will <b>not</b> correctly handle: PDFs with a compressed
 * ({@code FlateDecode}) embedded-file stream (very common from real PDF
 * libraries), an indirect {@code /Length} reference (i.e. {@code /Length 9 0 R}
 * rather than a literal number), or more than one embedded file where the
 * XML isn't the first one found. Handling the general case is exactly the
 * kind of thing a real PDF library (Apache PDFBox, Mustangproject) exists
 * for — see the README for why this project didn't pull one in.
 */
public class ZugferdParser {

    private static final Pattern EMBEDDED_FILE_OBJECT = Pattern.compile(
            "/Type\\s*/EmbeddedFile.*?/Length\\s+(\\d+).*?stream\\r?\\n",
            Pattern.DOTALL
    );

    public static class ExtractionException extends RuntimeException {
        public ExtractionException(String message) {
            super(message);
        }
    }

    /**
     * @return the extracted XML bytes
     * @throws ExtractionException if no embedded-file stream could be located
     */
    public byte[] extractEmbeddedXml(byte[] pdfBytes) {
        // Search in the ISO-8859-1 (Latin-1) view of the bytes: it's a 1:1 byte<->char
        // mapping, so regex-matched character offsets translate directly back to byte
        // offsets in the original array — unlike UTF-8, where multi-byte sequences
        // would shift offsets and corrupt the slice below.
        String latin1View = new String(pdfBytes, StandardCharsets.ISO_8859_1);
        Matcher matcher = EMBEDDED_FILE_OBJECT.matcher(latin1View);

        if (!matcher.find()) {
            throw new ExtractionException(
                    "No /EmbeddedFile stream found in the supplied PDF (or its /Length is not "
                            + "a literal number this extractor can read — see class javadoc for scope).");
        }

        int length = Integer.parseInt(matcher.group(1));
        int streamStart = matcher.end();
        if (streamStart + length > pdfBytes.length) {
            throw new ExtractionException(
                    "Declared embedded-file /Length (" + length + ") runs past the end of the PDF — "
                            + "the file may be truncated or corrupted.");
        }

        byte[] extracted = new byte[length];
        System.arraycopy(pdfBytes, streamStart, extracted, 0, length);
        return extracted;
    }

    /** Convenience: extract the embedded XML and parse it in one step. */
    public com.tsegabrehan.einvoicing.domain.SourceInvoice parse(byte[] pdfBytes) {
        byte[] xml = extractEmbeddedXml(pdfBytes);
        return new XRechnungParser().parse(xml);
    }
}
