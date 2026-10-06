package com.afshin.nlrag.ingestion;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentLoaderServiceTest {

    private final DocumentLoaderService loader = new DocumentLoaderService();

    @Test
    void extractsPlainText() {
        String text = loader.extractText(
                new ByteArrayInputStream("Hello  IND\n\n\nworld".getBytes()),
                "note.txt",
                10_000);
        assertThat(text).contains("Hello").contains("world");
    }
}
