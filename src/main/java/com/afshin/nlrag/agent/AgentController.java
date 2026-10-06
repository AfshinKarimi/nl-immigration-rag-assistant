package com.afshin.nlrag.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the agent (tool-calling) entry point over REST, separate from
 * {@link com.afshin.nlrag.controller.AssistantController}, which always
 * runs the fixed RAG pipeline. Hitting this endpoint lets the LLM decide
 * whether to search documents, do a date calculation, both, or neither.
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final ImmigrationAgentService agentService;

    public AgentController(ImmigrationAgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String reply = agentService.chat(request.message());
        return new ChatResponse(reply);
    }

    public record ChatRequest(
            @NotBlank @Size(max = 4000) String message
    ) {
    }

    public record ChatResponse(String reply) {
    }
}
