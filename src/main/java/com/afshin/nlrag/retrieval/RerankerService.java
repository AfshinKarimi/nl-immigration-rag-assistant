package com.afshin.nlrag.retrieval;

import com.afshin.nlrag.model.RetrievedChunk;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Re-scores the Top-K candidates from {@link HybridSearchService} using a
 * cross-encoder reranking model — one that looks at (query, document) pairs
 * jointly, rather than comparing independently-computed embeddings. This is
 * more accurate but far more expensive per-pair than vector search, which
 * is exactly why it only runs on the narrowed-down Top-K set rather than
 * the whole corpus (see the diagram's "Reranker: More Accurate but Slower").
 *
 * <p>Wired here against the Cohere Rerank API as a concrete example; swap
 * the {@code callRerankApi} implementation for a local ONNX cross-encoder
 * if you want to avoid an external dependency.</p>
 */
@Service
public class RerankerService {

    private final RestClient restClient;
    private final String apiKey;

    public RerankerService(RestClient.Builder restClientBuilder,
                            @Value("${nlrag.reranker.api-key:}") String apiKey,
                            @Value("${nlrag.reranker.base-url:https://api.cohere.com/v1}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    /**
     * Reranks candidates and returns the top {@code topN}, ordered by
     * rerank score descending.
     */
    @CircuitBreaker(name = "llmService", fallbackMethod = "fallbackToHybridOrder")
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        if (apiKey == null || apiKey.isBlank()) {
            return fallbackToHybridOrder(query, candidates, topN, null);
        }

        List<Double> scores = callRerankApi(query, candidates);

        List<RetrievedChunk> rescored = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            RetrievedChunk c = candidates.get(i);
            rescored.add(new RetrievedChunk(
                    c.id(), c.content(), c.source(), c.page(), c.section(),
                    c.vectorScore(), c.keywordScore(), scores.get(i)
            ));
        }

        return rescored.stream()
                .sorted((a, b) -> Double.compare(b.rerankScore(), a.rerankScore()))
                .limit(topN)
                .toList();
    }

    /** Calls the external cross-encoder rerank API. */
    @SuppressWarnings("unchecked")
    private List<Double> callRerankApi(String query, List<RetrievedChunk> candidates) {
        List<String> documents = candidates.stream().map(RetrievedChunk::content).toList();

        Map<String, Object> response = restClient.post()
                .uri("/rerank")
                .header("Authorization", "Bearer " + apiKey)
                .body(Map.of(
                        "model", "rerank-english-v3.0",
                        "query", query,
                        "documents", documents,
                        "top_n", documents.size()
                ))
                .retrieve()
                .body(Map.class);

        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");

        // Results come back with an "index" (position in the original list)
        // and "relevance_score" — reorder into original-list order.
        Double[] ordered = new Double[candidates.size()];
        for (Map<String, Object> result : results) {
            int index = (Integer) result.get("index");
            double score = ((Number) result.get("relevance_score")).doubleValue();
            ordered[index] = score;
        }
        return List.of(ordered);
    }

    /**
     * Fallback if the reranker API is unavailable: fall back to the hybrid
     * search's own weighted score rather than failing the whole request.
     * A degraded but available system beats a hard failure here.
     */
    private List<RetrievedChunk> fallbackToHybridOrder(String query, List<RetrievedChunk> candidates,
                                                        int topN, Throwable t) {
        return candidates.stream()
                .map(c -> {
                    double hybrid = 0.7 * c.vectorScore() + 0.3 * c.keywordScore();
                    return new RetrievedChunk(
                            c.id(), c.content(), c.source(), c.page(), c.section(),
                            c.vectorScore(), c.keywordScore(), hybrid);
                })
                .sorted((a, b) -> Double.compare(b.rerankScore(), a.rerankScore()))
                .limit(topN)
                .toList();
    }
}
