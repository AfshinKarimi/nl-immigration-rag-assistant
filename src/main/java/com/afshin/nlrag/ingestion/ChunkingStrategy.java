package com.afshin.nlrag.ingestion;

import java.util.List;

/**
 * Strategy interface for splitting raw document text into chunks suitable
 * for embedding.
 *
 * <p>Different strategies trade off differently between semantic coherence
 * and implementation simplicity — see {@code docs/architecture-decisions.md}
 * for why this project defaults to {@link RecursiveChunkingStrategy} for
 * structured legal/procedural text (IND documents, labour-law articles)
 * rather than naive fixed-size splitting.</p>
 */
public interface ChunkingStrategy {

    /**
     * Splits the given text into chunks.
     *
     * @param text           the normalized document text
     * @param chunkSizeChars target chunk size, in characters (approximation
     *                       of token budget — see README for the mapping used)
     * @param overlapChars   number of characters to overlap between
     *                       consecutive chunks, preserving context across
     *                       chunk boundaries
     */
    List<String> chunk(String text, int chunkSizeChars, int overlapChars);
}
