package com.afshin.nlrag.retrieval;

import com.afshin.nlrag.model.RetrievedChunk;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Combines dense vector search (semantic similarity via pgvector) with
 * sparse keyword search (Postgres full-text / BM25-style ranking) and
 * merges the two result sets.
 *
 * <p>Pure vector search can miss exact terms that matter a lot in a legal
 * domain — e.g. a specific form code like "IND aanvraagformulier 7500" — so
 * combining it with keyword search improves recall on this kind of
 * terminology-heavy content, at the cost of extra query complexity.</p>
 */
@Service
public class HybridSearchService {

    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate jdbcTemplate;

    private static final double VECTOR_WEIGHT = 0.7;
    private static final double KEYWORD_WEIGHT = 0.3;

    public HybridSearchService(EmbeddingModel embeddingModel, JdbcTemplate jdbcTemplate) {
        this.embeddingModel = embeddingModel;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Runs both search modes and returns a merged, deduplicated candidate
     * list ordered by a weighted combination of the two scores. This is the
     * "Top-K Candidates" stage in the architecture diagram.
     */
    public List<RetrievedChunk> search(String query, int topK) {
        float[] queryEmbedding = embedText(query);

        List<RetrievedChunk> vectorResults = vectorSearch(queryEmbedding, topK);
        List<RetrievedChunk> keywordResults = keywordSearch(query, topK);

        return mergeResults(vectorResults, keywordResults, topK);
    }

    @Cacheable(cacheNames = "queryEmbeddings", key = "#query")
    @CircuitBreaker(name = "embeddingService")
    protected float[] embedText(String query) {
        return embeddingModel.embed(query);
    }

    private List<RetrievedChunk> vectorSearch(float[] embedding, int topK) {
        String sql = """
                SELECT id::text AS id, content,
                       COALESCE(metadata->>'source', '') AS source,
                       NULLIF(metadata->>'page', '')::integer AS page,
                       metadata->>'section' AS section,
                       1 - (embedding <=> ?::vector) AS score
                FROM document_chunks
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;
        String vectorLiteral = toVectorLiteral(embedding);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new RetrievedChunk(
                rs.getString("id"),
                rs.getString("content"),
                rs.getString("source"),
                rs.getObject("page") == null ? null : rs.getInt("page"),
                rs.getString("section"),
                rs.getDouble("score"),
                0.0,
                0.0
        ), vectorLiteral, vectorLiteral, topK);
    }

    private List<RetrievedChunk> keywordSearch(String query, int topK) {
        String sql = """
                SELECT id::text AS id, content,
                       COALESCE(metadata->>'source', '') AS source,
                       NULLIF(metadata->>'page', '')::integer AS page,
                       metadata->>'section' AS section,
                       ts_rank(content_tsv, plainto_tsquery('english', ?)) AS score
                FROM document_chunks
                WHERE content_tsv @@ plainto_tsquery('english', ?)
                ORDER BY score DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new RetrievedChunk(
                rs.getString("id"),
                rs.getString("content"),
                rs.getString("source"),
                rs.getObject("page") == null ? null : rs.getInt("page"),
                rs.getString("section"),
                0.0,
                rs.getDouble("score"),
                0.0
        ), query, query, topK);
    }

    private List<RetrievedChunk> mergeResults(List<RetrievedChunk> vectorResults,
                                               List<RetrievedChunk> keywordResults,
                                               int topK) {
        Map<String, RetrievedChunk> merged = new LinkedHashMap<>();

        for (RetrievedChunk chunk : vectorResults) {
            merged.put(chunk.id(), chunk);
        }
        for (RetrievedChunk chunk : keywordResults) {
            merged.merge(chunk.id(), chunk, (existing, incoming) -> new RetrievedChunk(
                    existing.id(), existing.content(), existing.source(), existing.page(), existing.section(),
                    existing.vectorScore(), incoming.keywordScore(), 0.0
            ));
        }

        return merged.values().stream()
                .sorted((a, b) -> Double.compare(weightedScore(b), weightedScore(a)))
                .limit(topK)
                .toList();
    }

    private double weightedScore(RetrievedChunk chunk) {
        return VECTOR_WEIGHT * chunk.vectorScore() + KEYWORD_WEIGHT * chunk.keywordScore();
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        return sb.append("]").toString();
    }
}
