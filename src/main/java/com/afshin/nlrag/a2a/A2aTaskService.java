package com.afshin.nlrag.a2a;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory A2A task store. Completes each {@code message/send} synchronously
 * by calling {@link com.afshin.nlrag.agent.ImmigrationAgentService} — no second RAG pipeline.
 */
@Service
public class A2aTaskService {

    static final int MAX_INPUT_CHARS = 4000;
    private static final int MAX_TASKS = 100;

    private final A2aAgentTurn completeTurn;
    private final Map<String, Map<String, Object>> tasks = new ConcurrentHashMap<>();
    private final List<String> insertionOrder = new ArrayList<>();

    public A2aTaskService(A2aAgentTurn agentTurn) {
        this.completeTurn = agentTurn;
    }

    public Map<String, Object> sendMessage(String userText, String contextIdHint) {
        String text = userText == null ? "" : userText.trim();
        if (text.isBlank()) {
            throw new A2aProtocolException(-32602, "message has no text parts");
        }
        if (text.length() > MAX_INPUT_CHARS) {
            throw new A2aProtocolException(-32602, "message text exceeds " + MAX_INPUT_CHARS + " characters");
        }

        String taskId = UUID.randomUUID().toString();
        String contextId = (contextIdHint == null || contextIdHint.isBlank())
                ? UUID.randomUUID().toString()
                : contextIdHint;
        String userMessageId = UUID.randomUUID().toString();
        String agentMessageId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Map<String, Object> userMessage = message("user", userMessageId, taskId, contextId, text);
        try {
            String reply = completeTurn.complete(text);
            if (reply == null || reply.isBlank()) {
                reply = "(empty agent reply)";
            }
            Map<String, Object> agentMessage = message("agent", agentMessageId, taskId, contextId, reply);
            Map<String, Object> artifact = artifact(reply);
            Map<String, Object> task = task(
                    taskId,
                    contextId,
                    "completed",
                    now,
                    List.of(userMessage, agentMessage),
                    List.of(artifact)
            );
            store(taskId, task);
            return task;
        } catch (A2aProtocolException e) {
            throw e;
        } catch (RuntimeException e) {
            Map<String, Object> errMsg = message(
                    "agent",
                    agentMessageId,
                    taskId,
                    contextId,
                    "Agent failed: " + e.getMessage()
            );
            Map<String, Object> failed = task(
                    taskId,
                    contextId,
                    "failed",
                    Instant.now(),
                    List.of(userMessage, errMsg),
                    List.of()
            );
            store(taskId, failed);
            return failed;
        }
    }

    public Map<String, Object> getTask(String taskId) {
        Map<String, Object> task = tasks.get(taskId);
        if (task == null) {
            throw new A2aProtocolException(-32001, "Task not found: " + taskId);
        }
        return task;
    }

    public List<Map<String, Object>> listTasks() {
        synchronized (insertionOrder) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (String id : insertionOrder) {
                Map<String, Object> t = tasks.get(id);
                if (t != null) {
                    out.add(t);
                }
            }
            return out;
        }
    }

    public Map<String, Object> cancelTask(String taskId) {
        Map<String, Object> existing = getTask(taskId);
        String state = String.valueOf(statusState(existing));
        if ("completed".equals(state) || "failed".equals(state) || "canceled".equals(state)) {
            throw new A2aProtocolException(-32002, "Task is already terminal: " + state);
        }
        return existing;
    }

    private void store(String taskId, Map<String, Object> task) {
        synchronized (insertionOrder) {
            tasks.put(taskId, task);
            insertionOrder.add(taskId);
            while (insertionOrder.size() > MAX_TASKS) {
                String oldest = insertionOrder.remove(0);
                tasks.remove(oldest);
            }
        }
    }

    private static Object statusState(Map<String, Object> task) {
        Object status = task.get("status");
        if (status instanceof Map<?, ?> map) {
            return map.get("state");
        }
        return "unknown";
    }

    private static Map<String, Object> task(
            String id,
            String contextId,
            String state,
            Instant timestamp,
            List<Map<String, Object>> history,
            List<Map<String, Object>> artifacts
    ) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("state", state);
        status.put("timestamp", timestamp.toString());
        if (!history.isEmpty()) {
            status.put("message", history.get(history.size() - 1));
        }

        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", id);
        task.put("contextId", contextId);
        task.put("kind", "task");
        task.put("status", status);
        task.put("history", history);
        task.put("artifacts", artifacts);
        return task;
    }

    private static Map<String, Object> message(
            String role,
            String messageId,
            String taskId,
            String contextId,
            String text
    ) {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("kind", "text");
        part.put("text", text);

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("kind", "message");
        msg.put("role", role);
        msg.put("messageId", messageId);
        msg.put("taskId", taskId);
        msg.put("contextId", contextId);
        msg.put("parts", List.of(part));
        return msg;
    }

    private static Map<String, Object> artifact(String text) {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("kind", "text");
        part.put("text", text);

        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("artifactId", UUID.randomUUID().toString());
        artifact.put("name", "agent-reply");
        artifact.put("parts", List.of(part));
        return artifact;
    }
}
