package com.afshin.nlrag.generation;

import com.afshin.nlrag.model.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the final prompt sent to the LLM: system instructions, the
 * retrieved & reranked context (each chunk tagged with its citation), and
 * the user's question — matching the "Prompt to LLM" box in the
 * architecture diagram.
 */
@Component
public class PromptBuilder {

    private static final String SYSTEM_PROMPT = """
            You are an assistant that helps people understand Dutch immigration
            procedures and labour-law basics. Answer ONLY using the provided
            context. If the context does not contain the answer, say you are
            not sure rather than guessing. Always mention which source
            document(s) you based the answer on.
            """;

    public String build(String question, List<RetrievedChunk> context) {
        String contextBlock = context.stream()
                .map(chunk -> "- [%s] %s".formatted(chunk.citation(), chunk.content()))
                .collect(Collectors.joining("\n"));

        return """
                System: %s

                Context:
                %s

                Question: %s
                Answer:
                """.formatted(SYSTEM_PROMPT, contextBlock, question);
    }
}
