package com.afshin.nlrag.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON-RPC 2.0 dispatcher for a subset of A2A methods. Streaming and push
 * notifications are not implemented and are advertised as {@code false} on
 * the Agent Card.
 */
@Service
public class A2aJsonRpcService {

    private final A2aTaskService tasks;

    public A2aJsonRpcService(A2aTaskService tasks) {
        this.tasks = tasks;
    }

    public Map<String, Object> handle(JsonNode request) {
        if (request == null || request.isArray()) {
            return error(null, -32600, "Batch JSON-RPC is not supported");
        }
        if (!request.isObject()) {
            return error(null, -32600, "Invalid JSON-RPC request");
        }
        Object id = jsonRpcId(request.get("id"));
        String version = request.path("jsonrpc").asText("");
        if (!"2.0".equals(version)) {
            return error(id, -32600, "jsonrpc must be \"2.0\"");
        }
        String method = request.path("method").asText("");
        JsonNode params = request.get("params");
        try {
            return switch (normalize(method)) {
                case "message/send" -> ok(id, send(params));
                case "tasks/get" -> ok(id, tasks.getTask(requiredId(params, "id")));
                case "tasks/list" -> ok(id, Map.of("tasks", tasks.listTasks()));
                case "tasks/cancel" -> ok(id, tasks.cancelTask(requiredId(params, "id")));
                case "message/stream" -> error(id, -32601, "Streaming is not supported (capabilities.streaming=false)");
                default -> error(id, -32601, "Method not found: " + method);
            };
        } catch (A2aProtocolException e) {
            return error(id, e.code(), e.getMessage());
        }
    }

    Map<String, Object> send(JsonNode params) {
        if (params == null || !params.isObject()) {
            throw new A2aProtocolException(-32602, "params.message is required");
        }
        JsonNode message = params.get("message");
        String text = A2aTextExtractor.fromMessage(message);
        String contextId = message != null ? message.path("contextId").asText("") : "";
        return tasks.sendMessage(text, contextId);
    }

    private static String requiredId(JsonNode params, String field) {
        if (params == null || !params.hasNonNull(field)) {
            throw new A2aProtocolException(-32602, "params." + field + " is required");
        }
        return params.get(field).asText();
    }

    private static String normalize(String method) {
        return switch (method) {
            case "SendMessage" -> "message/send";
            case "GetTask" -> "tasks/get";
            case "ListTasks" -> "tasks/list";
            case "CancelTask" -> "tasks/cancel";
            case "SendStreamingMessage" -> "message/stream";
            default -> method;
        };
    }

    private static Object jsonRpcId(JsonNode idNode) {
        if (idNode == null || idNode.isNull()) {
            return null;
        }
        if (idNode.isIntegralNumber()) {
            return idNode.longValue();
        }
        if (idNode.isNumber()) {
            double d = idNode.doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) {
                return (long) d;
            }
            return d;
        }
        return idNode.asText();
    }

    private static Map<String, Object> ok(Object id, Object result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("result", result);
        return body;
    }

    static Map<String, Object> error(Object id, int code, String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", message);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("error", err);
        return body;
    }
}
