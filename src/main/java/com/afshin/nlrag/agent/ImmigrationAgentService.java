package com.afshin.nlrag.agent;

import com.afshin.nlrag.tools.ImmigrationTools;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * The agentic entry point: instead of always running the fixed
 * Hybrid-Search -&gt; Rerank -&gt; Generate pipeline
 * ({@link com.afshin.nlrag.retrieval.RagQueryService}), this service hands
 * the LLM a set of tools ({@link ImmigrationTools}) and lets it decide,
 * per user turn, which tool(s) to call and in what order.
 *
 * <p>This is the "AI Agent" distinct from "RAG" in the architecture: RAG is
 * one specific tool the agent has available (document search); the agent
 * can also reach for a date calculator, chain multiple tool calls together,
 * or answer directly with no tool call at all if the question doesn't need
 * one — a decision the fixed pipeline never makes.</p>
 */
@Service
public class ImmigrationAgentService {

    private static final String SYSTEM_PROMPT = """
            You are an agent that helps people navigate Dutch immigration and
            labour-law questions. You have tools available for searching
            official documentation and for date/duration calculations.
            Decide which tool(s), if any, are needed to answer the user's
            question. Only use searchImmigrationDocs for questions about
            rules or procedures. Use the date tools for date-arithmetic
            questions. If a question needs no tool, answer directly but
            briefly note that it wasn't grounded in a specific document.
            """;

    private final ChatClient chatClient;

    public ImmigrationAgentService(ChatClient.Builder chatClientBuilder, ImmigrationTools tools) {
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(tools)
                .build();
    }

    @CircuitBreaker(name = "llmService")
    @Retry(name = "llmService")
    public String chat(String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                .call()
                .content();
    }
}
