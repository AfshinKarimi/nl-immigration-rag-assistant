package com.afshin.nlrag.generation;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around the Spring AI {@link ChatClient} for the final
 * "LLM — Generate Answer" step, with resilience annotations so a transient
 * failure on the LLM provider doesn't take down the whole request path.
 */
@Service
public class AnswerGenerationService {

    private final ChatClient chatClient;

    public AnswerGenerationService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @CircuitBreaker(name = "llmService")
    @Retry(name = "llmService")
    public String generate(String prompt) {
        return chatClient.prompt(prompt).call().content();
    }
}
