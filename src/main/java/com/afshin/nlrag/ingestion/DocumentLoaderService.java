package com.afshin.nlrag.ingestion;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParser;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.parser.txt.TXTParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.ContentHandler;

import java.io.InputStream;
import java.util.List;

/**
 * Extracts plain text from an allowlisted set of formats only (text, HTML,
 * PDF, DOCX). Full {@code AutoDetectParser} + tika-parsers-standard is
 * intentionally not used: that package can open archives and other
 * high-risk types.
 */
@Service
public class DocumentLoaderService {

    private final Parser parser = restrictedParser();

    /**
     * Extracts text from the given input stream and normalizes whitespace.
     *
     * @param input       raw document bytes
     * @param sourceName  original filename (helps Tika pick a parser)
     * @param maxChars    safety cap so a pathological document can't blow
     *                    up memory during a batch ingestion run
     */
    public String extractText(InputStream input, String sourceName, int maxChars) {
        try {
            ContentHandler handler = new BodyContentHandler(maxChars);
            Metadata metadata = new Metadata();
            if (sourceName != null && !sourceName.isBlank()) {
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, sourceName);
            }
            ParseContext context = new ParseContext();
            context.set(Parser.class, parser);
            parser.parse(input, handler, metadata, context);
            return normalize(handler.toString());
        } catch (Exception e) {
            throw new DocumentLoadException("Failed to extract text from document", e);
        }
    }

    /** @deprecated use {@link #extractText(InputStream, String, int)} */
    public String extractText(InputStream input, int maxChars) {
        return extractText(input, null, maxChars);
    }

    private static Parser restrictedParser() {
        Parser composite = new CompositeParser(
                org.apache.tika.mime.MediaTypeRegistry.getDefaultRegistry(),
                List.of(new TXTParser(), new HtmlParser(), new PDFParser(), new OOXMLParser()));
        // Detect MIME, then parse only with the allowlisted parsers above.
        return new AutoDetectParser(composite);
    }

    private String normalize(String raw) {
        return raw
                .replace("\r\n", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    public static class DocumentLoadException extends RuntimeException {
        public DocumentLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
