package com.afshin.nlrag.ingestion;

import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Orchestrates the full ingestion pipeline: Document Loader -> Chunking ->
 * Embedding -> Vector DB, matching section 1 of the architecture diagram.
 *
 * <p>Ingestion of many source documents (IND pages, labour-law PDFs, etc.)
 * is I/O-bound (parsing + embedding API calls), so each document is
 * processed on a virtual thread to keep throughput high without tuning a
 * traditional thread pool.</p>
 */
@Service
public class IngestionPipeline {

    private final DocumentLoaderService documentLoaderService;
    private final ChunkingStrategy chunkingStrategy;
    private final VectorStore vectorStore;
    private final int chunkSizeChars;
    private final int overlapChars;

    // Virtual-thread executor: cheap enough to spin up one per ingested file.
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public IngestionPipeline(
            DocumentLoaderService documentLoaderService,
            @Qualifier("recursiveChunkingStrategy") ChunkingStrategy chunkingStrategy,
            VectorStore vectorStore,
            @Value("${nlrag.chunking.chunk-size-tokens:500}") int chunkSizeTokens,
            @Value("${nlrag.chunking.chunk-overlap-tokens:100}") int overlapTokens
    ) {
        this.documentLoaderService = documentLoaderService;
        this.chunkingStrategy = chunkingStrategy;
        this.vectorStore = vectorStore;
        // Rough heuristic: ~4 characters per token for English/Dutch text.
        this.chunkSizeChars = chunkSizeTokens * 4;
        this.overlapChars = overlapTokens * 4;
    }

    /**
     * Ingests a single document synchronously (used by tests and the
     * bulk-ingestion CLI runner alike — see {@link #ingestBatch}).
     */
    @Retry(name = "embeddingService")
    public void ingest(InputStream input, String sourceName, Map<String, Object> extraMetadata) {
        String text = documentLoaderService.extractText(input, sourceName, 10_000_000);
        List<String> rawChunks = chunkingStrategy.chunk(text, chunkSizeChars, overlapChars);

        List<Document> documents = rawChunks.stream()
                .map(chunkText -> {
                    Map<String, Object> metadata = new java.util.HashMap<>(extraMetadata);
                    metadata.put("source", sourceName);
                    return new Document(chunkText, metadata);
                })
                .collect(Collectors.toList());

        vectorStore.add(documents); // Spring AI computes embeddings and writes to pgvector.
    }

    /**
     * Ingests multiple documents concurrently using virtual threads.
     * Returns a future that completes once every document has been
     * embedded and stored.
     */
    public CompletableFuture<Void> ingestBatch(List<IngestionRequest> requests) {
        List<CompletableFuture<Void>> futures = requests.stream()
                .map(req -> CompletableFuture.runAsync(
                        () -> ingest(req.input(), req.sourceName(), req.metadata()), executor))
                .toList();
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    public record IngestionRequest(InputStream input, String sourceName, Map<String, Object> metadata) {
    }
}
