package com.afshin.nlrag.ingestion;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Naive fixed-size chunking: splits text into windows of a fixed character
 * length with a fixed overlap, ignoring sentence/paragraph boundaries.
 *
 * <p>Simple and fast, but can split a sentence — or a legal clause — right
 * down the middle, which hurts retrieval quality on structured documents.
 * Kept here as a baseline for the evaluation harness in
 * {@code docs/evaluation-results.md} to compare against
 * {@link RecursiveChunkingStrategy}.</p>
 */
@Component("fixedSizeChunkingStrategy")
public class FixedSizeChunkingStrategy implements ChunkingStrategy {

    @Override
    public List<String> chunk(String text, int chunkSizeChars, int overlapChars) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        int start = 0;
        int length = text.length();
        while (start < length) {
            int end = Math.min(start + chunkSizeChars, length);
            chunks.add(text.substring(start, end).trim());
            if (end == length) {
                break;
            }
            start = end - overlapChars;
        }
        return chunks;
    }
}
