package com.afshin.nlrag.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadValidatorTest {

    private UploadValidator validator;

    @BeforeEach
    void setUp() {
        validator = new UploadValidator(1024);
    }

    @Test
    void acceptsPlainText() {
        validator.validate(new MockMultipartFile(
                "file", "faq.txt", "text/plain", "The 30% ruling lasts five years.".getBytes()));
    }

    @Test
    void acceptsPdfMagic() {
        validator.validate(new MockMultipartFile(
                "file", "guide.pdf", "application/pdf", "%PDF-1.7 fake body".getBytes()));
    }

    @Test
    void rejectsEmpty() {
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile(
                "file", "faq.txt", "text/plain", new byte[0])))
                .isInstanceOf(RejectedUploadException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsOversized() {
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile(
                "file", "faq.txt", "text/plain", "x".repeat(2000).getBytes())))
                .isInstanceOf(RejectedUploadException.class)
                .hasMessageContaining("max size");
    }

    @Test
    void rejectsDisallowedExtension() {
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile(
                "file", "payload.zip", "application/zip", "PK\u0003\u0004".getBytes())))
                .isInstanceOf(RejectedUploadException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void rejectsPdfExtensionWithWrongMagic() {
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile(
                "file", "guide.pdf", "application/pdf", "not a pdf".getBytes())))
                .isInstanceOf(RejectedUploadException.class)
                .hasMessageContaining("do not match");
    }

    @Test
    void stripsPathFromFilename() {
        assertThat(UploadValidator.safeFilename("C:\\\\temp\\\\..\\\\faq.txt")).isEqualTo("faq.txt");
        assertThat(UploadValidator.safeFilename("../../etc/passwd.txt")).isEqualTo("passwd.txt");
    }
}
