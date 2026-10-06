package com.afshin.nlrag.controller;

import com.afshin.nlrag.ingestion.IngestionPipeline;
import com.afshin.nlrag.ingestion.RejectedUploadException;
import com.afshin.nlrag.ingestion.UploadValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionController {

    private static final int MAX_SECTION_LENGTH = 64;

    private final IngestionPipeline ingestionPipeline;
    private final UploadValidator uploadValidator;

    public IngestionController(IngestionPipeline ingestionPipeline, UploadValidator uploadValidator) {
        this.ingestionPipeline = ingestionPipeline;
        this.uploadValidator = uploadValidator;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file,
                                          @RequestParam(value = "section", required = false) String section)
            throws IOException {
        uploadValidator.validate(file);
        String filename = UploadValidator.safeFilename(file.getOriginalFilename());
        ingestionPipeline.ingest(
                file.getInputStream(),
                filename,
                Map.of("section", sanitizeSection(section))
        );
        return ResponseEntity.ok("Ingested: " + filename);
    }

    private static String sanitizeSection(String section) {
        if (section == null || section.isBlank()) {
            return "general";
        }
        String trimmed = section.trim();
        if (trimmed.length() > MAX_SECTION_LENGTH) {
            throw new RejectedUploadException("section must be at most " + MAX_SECTION_LENGTH + " characters");
        }
        if (!trimmed.matches("[A-Za-z0-9._-]+")) {
            throw new RejectedUploadException("section contains invalid characters");
        }
        return trimmed;
    }
}
