package com.afshin.nlrag.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Rejects uploads before Tika runs: size cap, extension allowlist, and a
 * cheap magic-byte check so Content-Type spoofing is not enough.
 */
@Component
public class UploadValidator {

    static final Set<String> ALLOWED_EXTENSIONS = Set.of("txt", "pdf", "html", "htm", "docx");

    static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.TEXT_PLAIN_VALUE,
            MediaType.APPLICATION_PDF_VALUE,
            MediaType.TEXT_HTML_VALUE,
            MediaType.APPLICATION_XHTML_XML_VALUE,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            MediaType.APPLICATION_OCTET_STREAM_VALUE
    );

    private final long maxFileBytes;

    public UploadValidator(@Value("${nlrag.ingestion.max-file-bytes:5242880}") long maxFileBytes) {
        this.maxFileBytes = maxFileBytes;
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RejectedUploadException("Upload is empty");
        }
        if (file.getSize() > maxFileBytes) {
            throw new RejectedUploadException(
                    "File exceeds max size of " + maxFileBytes + " bytes");
        }

        String filename = safeFilename(file.getOriginalFilename());
        String extension = extensionOf(filename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new RejectedUploadException(
                    "File type not allowed. Use: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            String baseType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_CONTENT_TYPES.contains(baseType)) {
                throw new RejectedUploadException("Content-Type not allowed: " + baseType);
            }
        }

        byte[] header = peek(file, 64);
        if (!magicMatches(extension, header)) {
            throw new RejectedUploadException(
                    "File contents do not match extension ." + extension);
        }
    }

    public static String safeFilename(String original) {
        if (original == null || original.isBlank()) {
            throw new RejectedUploadException("Filename is required");
        }
        String name = Path.of(original.replace('\\', '/')).getFileName().toString();
        if (name.isBlank() || name.contains("..")) {
            throw new RejectedUploadException("Invalid filename");
        }
        return name;
    }

    static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 1 || dot == filename.length() - 1) {
            throw new RejectedUploadException("File must have an allowed extension");
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static byte[] peek(MultipartFile file, int n) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(n);
        } catch (IOException e) {
            throw new RejectedUploadException("Could not read upload");
        }
    }

    static boolean magicMatches(String extension, byte[] header) {
        if (header == null || header.length == 0) {
            return false;
        }
        return switch (extension) {
            case "pdf" -> startsWith(header, "%PDF");
            case "docx" -> header.length >= 4
                    && header[0] == 'P' && header[1] == 'K'
                    && header[2] == 0x03 && header[3] == 0x04;
            case "html", "htm" -> {
                String prefix = new String(header, StandardCharsets.US_ASCII)
                        .toLowerCase(Locale.ROOT)
                        .stripLeading();
                yield prefix.startsWith("<");
            }
            case "txt" -> !containsNul(header);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] header, String ascii) {
        byte[] expected = ascii.getBytes(StandardCharsets.US_ASCII);
        if (header.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (header[i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsNul(byte[] header) {
        for (byte b : header) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }
}
