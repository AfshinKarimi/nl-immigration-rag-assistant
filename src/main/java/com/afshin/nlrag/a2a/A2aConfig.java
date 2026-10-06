package com.afshin.nlrag.a2a;

import com.afshin.nlrag.agent.ImmigrationAgentService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class A2aConfig {

    @Bean
    A2aAgentTurn a2aAgentTurn(ImmigrationAgentService agentService) {
        return agentService::chat;
    }
}
