package com.afshin.nlrag.retrieval;

import com.afshin.nlrag.generation.AnswerGenerationService;
import com.afshin.nlrag.generation.PromptBuilder;
import com.afshin.nlrag.model.AssistantAnswer;
import com.afshin.nlrag.model.RetrievedChunk;
import com.afshin.nlrag.observability.RagMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates the full query-time pipeline (section 2 of the architecture
 * diagram): Hybrid Search -> Rerank -> Confidence check -> Prompt Building
 * -> LLM generation -> citation-annotated answer.
 */
@Service
public class RagQueryService {

    private final HybridSearchService hybridSearchService;
    private final RerankerService rerankerService;
    private final ConfidenceScorer confidenceScorer;
    private final PromptBuilder promptBuilder;
    private final AnswerGenerationService answerGenerationService;
    private final RagMetrics metrics;
    private final int topK;
    private final int topN;

    public RagQueryService(
            HybridSearchService hybridSearchService,
            RerankerService rerankerService,
            ConfidenceScorer confidenceScorer,
            PromptBuilder promptBuilder,
            AnswerGenerationService answerGenerationService,
            RagMetrics metrics,
            @Value("${nlrag.retrieval.top-k:50}") int topK,
            @Value("${nlrag.retrieval.top-n:5}") int topN
    ) {
        this.hybridSearchService = hybridSearchService;
        this.rerankerService = rerankerService;
        this.confidenceScorer = confidenceScorer;
        this.promptBuilder = promptBuilder;
        this.answerGenerationService = answerGenerationService;
        this.metrics = metrics;
        this.topK = topK;
        this.topN = topN;
    }

    public AssistantAnswer answer(String question) {
        return metrics.timePipeline(() -> {
            List<RetrievedChunk> candidates = hybridSearchService.search(question, topK);
            List<RetrievedChunk> topResults = rerankerService.rerank(question, candidates, topN);

            if (!confidenceScorer.isConfident(topResults)) {
                metrics.recordLowConfidenceAnswer();
                return AssistantAnswer.lowConfidence(question);
            }

            String prompt = promptBuilder.build(question, topResults);
            String rawAnswer = answerGenerationService.generate(prompt);

            List<String> citations = topResults.stream().map(RetrievedChunk::citation).toList();
            metrics.recordConfidentAnswer();
            return new AssistantAnswer(question, rawAnswer, true, citations);
        });
    }
}
