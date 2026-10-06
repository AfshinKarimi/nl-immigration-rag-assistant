package com.afshin.nlrag.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Wraps the RAG query pipeline with Micrometer metrics so latency and
 * answer-confidence rate are visible in Prometheus/Grafana — the
 * "Observability" concern the README's architecture-decisions doc calls
 * out as a production requirement most demo RAG projects skip.
 */
@Component
public class RagMetrics {

    private final Timer pipelineTimer;
    private final Counter confidentAnswers;
    private final Counter lowConfidenceAnswers;

    public RagMetrics(MeterRegistry registry) {
        this.pipelineTimer = Timer.builder("nlrag.query.pipeline.duration")
                .description("End-to-end latency of the RAG query pipeline")
                .register(registry);
        this.confidentAnswers = Counter.builder("nlrag.query.answers")
                .tag("outcome", "confident")
                .register(registry);
        this.lowConfidenceAnswers = Counter.builder("nlrag.query.answers")
                .tag("outcome", "low_confidence")
                .register(registry);
    }

    public <T> T timePipeline(Supplier<T> pipeline) {
        return pipelineTimer.record(pipeline);
    }

    public void recordConfidentAnswer() {
        confidentAnswers.increment();
    }

    public void recordLowConfidenceAnswer() {
        lowConfidenceAnswers.increment();
    }
}
