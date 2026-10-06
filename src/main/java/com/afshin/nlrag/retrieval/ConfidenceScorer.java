package com.afshin.nlrag.retrieval;

import com.afshin.nlrag.model.RetrievedChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Decides whether the retrieved-and-reranked context is strong enough to
 * ground an answer, or whether the assistant should decline rather than
 * risk hallucinating — important in a domain (immigration/labour law) where
 * a wrong answer has real consequences for the user.
 */
@Component
public class ConfidenceScorer {

    private final double minConfidence;

    public ConfidenceScorer(@Value("${nlrag.retrieval.min-confidence:0.35}") double minConfidence) {
        this.minConfidence = minConfidence;
    }

    public boolean isConfident(List<RetrievedChunk> topResults) {
        if (topResults.isEmpty()) {
            return false;
        }
        double topScore = topResults.get(0).rerankScore();
        return topScore >= minConfidence;
    }
}
