package com.afshin.nlrag.ingestion;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive chunking: tries to split on paragraph boundaries first, then
 * falls back to sentence boundaries, and only falls back to a hard
 * character cut as a last resort.
 *
 * <p>This keeps each chunk semantically coherent (a full clause / paragraph
 * of an IND regulation, rather than half of one), which is what a
 * cross-encoder reranker and the LLM both benefit from downstream. This is
 * the default strategy used by {@link IngestionPipeline}.</p>
 */
@Component("recursiveChunkingStrategy")
public class RecursiveChunkingStrategy implements ChunkingStrategy {

    private static final String[] SEPARATORS = {"\n\n", "\n", ". ", " "};

    @Override
    public List<String> chunk(String text, int chunkSizeChars, int overlapChars) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        splitRecursively(text.trim(), chunkSizeChars, overlapChars, 0, result);
        return mergeWithOverlap(result, chunkSizeChars, overlapChars);
    }

    private void splitRecursively(String text, int chunkSizeChars, int overlapChars,
                                   int separatorIndex, List<String> out) {
        if (text.length() <= chunkSizeChars) {
            if (!text.isBlank()) {
                out.add(text.trim());
            }
            return;
        }

        if (separatorIndex >= SEPARATORS.length) {
            // Last resort: hard cut.
            out.add(text.substring(0, chunkSizeChars).trim());
            splitRecursively(text.substring(chunkSizeChars), chunkSizeChars, overlapChars,
                    separatorIndex, out);
            return;
        }

        String separator = SEPARATORS[separatorIndex];
        String[] parts = text.split(java.util.regex.Pattern.quote(separator));
        if (parts.length <= 1) {
            // This separator didn't help; try the next, finer-grained one.
            splitRecursively(text, chunkSizeChars, overlapChars, separatorIndex + 1, out);
            return;
        }

        StringBuilder buffer = new StringBuilder();
        for (String part : parts) {
            if (buffer.length() + part.length() + separator.length() > chunkSizeChars && buffer.length() > 0) {
                out.add(buffer.toString().trim());
                buffer = new StringBuilder();
            }
            buffer.append(part).append(separator);
        }
        if (!buffer.isEmpty()) {
            String remainder = buffer.toString().trim();
            if (remainder.length() > chunkSizeChars) {
                splitRecursively(remainder, chunkSizeChars, overlapChars, separatorIndex + 1, out);
            } else if (!remainder.isEmpty()) {
                out.add(remainder);
            }
        }
    }

    /**
     * Adds trailing-context overlap between consecutive chunks so retrieval
     * doesn't lose meaning at a chunk boundary.
     */
    private List<String> mergeWithOverlap(List<String> chunks, int chunkSizeChars, int overlapChars) {
        if (overlapChars <= 0 || chunks.size() < 2) {
            return chunks;
        }
        List<String> withOverlap = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String current = chunks.get(i);
            if (i == 0) {
                withOverlap.add(current);
                continue;
            }
            String previous = chunks.get(i - 1);
            String tail = previous.length() > overlapChars
                    ? previous.substring(previous.length() - overlapChars)
                    : previous;
            withOverlap.add((tail + " " + current).trim());
        }
        return withOverlap;
    }
}
