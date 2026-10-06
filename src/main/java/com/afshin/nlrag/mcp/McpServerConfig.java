package com.afshin.nlrag.mcp;

import com.afshin.nlrag.tools.ImmigrationTools;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Registers {@link ImmigrationTools} with the MCP server only.
 *
 * <p>Do not expose these as a {@code ToolCallbackProvider} {@code @Bean}.
 * Spring AI's {@code ToolCallingAutoConfiguration} injects every such
 * provider into the default {@code ChatModel}. That ChatModel is what
 * {@code RagQueryService} uses to generate answers, while
 * {@code searchImmigrationDocs} calls {@code RagQueryService} — a
 * constructor cycle, and it would also attach agent tools to the RAG
 * path where they do not belong.</p>
 *
 * <p>The in-process agent still receives the tools explicitly via
 * {@code ChatClient.Builder.defaultTools} in
 * {@link com.afshin.nlrag.agent.ImmigrationAgentService}.</p>
 */
@Configuration
public class McpServerConfig {

    @Bean
    public List<McpServerFeatures.SyncToolRegistration> immigrationMcpTools(
            ImmigrationTools immigrationTools) {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(immigrationTools)
                .build()
                .getToolCallbacks();
        return McpToolUtils.toSyncToolRegistration(callbacks);
    }
}
