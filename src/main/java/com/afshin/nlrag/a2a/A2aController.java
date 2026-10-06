package com.afshin.nlrag.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * A2A discovery (Agent Card) and JSON-RPC 2.0 endpoint. See ADR-11.
 */
@RestController
public class A2aController {

    private final A2aAgentCardService agentCardService;
    private final A2aJsonRpcService jsonRpcService;
    private final A2aTaskService taskService;

    public A2aController(
            A2aAgentCardService agentCardService,
            A2aJsonRpcService jsonRpcService,
            A2aTaskService taskService
    ) {
        this.agentCardService = agentCardService;
        this.jsonRpcService = jsonRpcService;
        this.taskService = taskService;
    }

    @GetMapping(value = {"/.well-known/agent-card.json", "/.well-known/agent.json"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> agentCard() {
        return agentCardService.card();
    }

    @PostMapping(value = "/a2a", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jsonRpc(@RequestBody JsonNode body) {
        return jsonRpcService.handle(body);
    }

    /**
     * HTTP+JSON convenience (A2A v1 {@code POST /message:send} style) so curl
     * does not need a JSON-RPC envelope.
     */
    @PostMapping(value = "/a2a/v1/message:send",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> messageSend(@RequestBody JsonNode body) {
        JsonNode message = body.has("message") ? body.get("message") : body;
        String text = A2aTextExtractor.fromMessage(message);
        String contextId = message.path("contextId").asText("");
        return taskService.sendMessage(text, contextId);
    }
}
