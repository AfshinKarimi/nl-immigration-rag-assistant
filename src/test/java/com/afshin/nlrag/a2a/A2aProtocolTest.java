package com.afshin.nlrag.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class A2aProtocolTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final A2aJsonRpcService rpc = new A2aJsonRpcService(
            new A2aTaskService(msg -> "echo:" + msg));

    @Test
    void extractsKindTextParts() throws Exception {
        var message = mapper.readTree("""
                {"role":"user","parts":[{"kind":"text","text":"Hello IND"}]}
                """);
        assertThat(A2aTextExtractor.fromMessage(message)).isEqualTo("Hello IND");
    }

    @Test
    void sendMessageCompletesTaskViaAgent() throws Exception {
        var request = mapper.readTree("""
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "method": "message/send",
                  "params": {
                    "message": {
                      "role": "user",
                      "messageId": "m1",
                      "kind": "message",
                      "parts": [{"kind": "text", "text": "How long is the 30% ruling?"}]
                    }
                  }
                }
                """);

        Map<String, Object> response = rpc.handle(request);
        assertThat(response.get("jsonrpc")).isEqualTo("2.0");
        assertThat(((Number) response.get("id")).longValue()).isEqualTo(1L);

        @SuppressWarnings("unchecked")
        Map<String, Object> task = (Map<String, Object>) response.get("result");
        @SuppressWarnings("unchecked")
        Map<String, Object> status = (Map<String, Object>) task.get("status");
        assertThat(status.get("state")).isEqualTo("completed");
        assertThat(task.get("artifacts")).asList().isNotEmpty();
        assertThat(task.get("id")).isInstanceOf(String.class);

        String taskId = (String) task.get("id");
        var get = mapper.readTree("""
                {"jsonrpc":"2.0","id":2,"method":"tasks/get","params":{"id":"%s"}}
                """.formatted(taskId));
        Map<String, Object> fetched = rpc.handle(get);
        @SuppressWarnings("unchecked")
        Map<String, Object> fetchedTask = (Map<String, Object>) fetched.get("result");
        assertThat(fetchedTask.get("id")).isEqualTo(taskId);
    }

    @Test
    void acceptsSendMessageAlias() throws Exception {
        var request = mapper.readTree("""
                {
                  "jsonrpc": "2.0",
                  "id": "abc",
                  "method": "SendMessage",
                  "params": {
                    "message": {
                      "parts": [{"text": "ping"}]
                    }
                  }
                }
                """);
        Map<String, Object> response = rpc.handle(request);
        assertThat(response).containsKey("result");
        assertThat(response.get("id")).isEqualTo("abc");
    }

    @Test
    void unknownMethodIsJsonRpcError() throws Exception {
        var request = mapper.readTree("""
                {"jsonrpc":"2.0","id":1,"method":"widgets/frob","params":{}}
                """);
        Map<String, Object> response = rpc.handle(request);
        assertThat(response).containsKey("error");
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertThat(error.get("code")).isEqualTo(-32601);
    }

    @Test
    void blankMessageIsInvalidParams() {
        A2aTaskService tasks = new A2aTaskService(msg -> msg);
        assertThatThrownBy(() -> tasks.sendMessage("  ", null))
                .isInstanceOf(A2aProtocolException.class)
                .hasMessageContaining("no text parts");
    }

    @Test
    void agentCardListsSkillsAndDisablesStreaming() {
        Map<String, Object> card = new A2aAgentCardService("http://localhost:8080/a2a").card();
        assertThat(card.get("url")).isEqualTo("http://localhost:8080/a2a");
        assertThat(card.get("skills")).asList().hasSize(2);
        @SuppressWarnings("unchecked")
        Map<String, Object> caps = (Map<String, Object>) card.get("capabilities");
        assertThat(caps.get("streaming")).isEqualTo(false);
        assertThat(caps.get("pushNotifications")).isEqualTo(false);
    }
}
