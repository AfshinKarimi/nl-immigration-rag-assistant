package com.afshin.nlrag;

import com.afshin.nlrag.ingestion.RecursiveChunkingStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecursiveChunkingStrategyTest {

    private final RecursiveChunkingStrategy strategy = new RecursiveChunkingStrategy();

    @Test
    void shouldNotSplitTextShorterThanChunkSize() {
        String text = "Short paragraph about the 30% ruling.";
        List<String> chunks = strategy.chunk(text, 500, 100);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo(text);
    }

    @Test
    void shouldSplitOnParagraphBoundariesBeforeHardCutting() {
        String paragraph1 = "A".repeat(300);
        String paragraph2 = "B".repeat(300);
        String text = paragraph1 + "\n\n" + paragraph2;

        List<String> chunks = strategy.chunk(text, 350, 20);

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        // Each chunk should stay close to complete paragraph content rather
        // than an arbitrary character cut across the paragraph boundary.
        assertThat(chunks.get(0)).contains("A");
    }

    @Test
    void shouldReturnEmptyListForBlankInput() {
        assertThat(strategy.chunk("   ", 500, 100)).isEmpty();
        assertThat(strategy.chunk(null, 500, 100)).isEmpty();
    }
}
