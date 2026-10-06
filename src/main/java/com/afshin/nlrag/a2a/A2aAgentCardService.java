package com.afshin.nlrag.a2a;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class A2aAgentCardService {

    private final String publicUrl;

    public A2aAgentCardService(
            @Value("${nlrag.a2a.public-url:http://localhost:8080/a2a}") String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public Map<String, Object> card() {
        Map<String, Object> provider = new LinkedHashMap<>();
        provider.put("organization", "Afshin — NL Immigration RAG demo");
        provider.put("url", "https://github.com/afshinkarimi");

        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("streaming", false);
        capabilities.put("pushNotifications", false);
        capabilities.put("stateTransitionHistory", false);

        Map<String, Object> qa = skill(
                "immigration-qa",
                "Dutch immigration Q&A",
                "Answers questions about Dutch immigration and labour procedures from ingested official text (demo, not legal advice). Internally uses the tool-calling agent and RAG pipeline.",
                List.of("immigration", "IND", "Netherlands", "visa", "HSM"),
                List.of(
                        "How long does the 30% ruling last?",
                        "What is a highly skilled migrant permit?"
                )
        );
        Map<String, Object> ruling = skill(
                "thirty-percent-ruling-estimate",
                "30% ruling remaining time",
                "Estimates remaining duration of a 5-year 30% ruling given an ISO start date. Not a Belastingdienst decision.",
                List.of("tax", "30-percent-ruling"),
                List.of("If my 30% ruling started 2022-03-01, how much time is left?")
        );

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("name", "NL Immigration RAG Assistant");
        card.put("description",
                "Peer agent for Dutch immigration procedure questions. "
                        + "A2A is a façade: work is delegated to ImmigrationAgentService "
                        + "(same tools as MCP). Not legal advice.");
        card.put("url", publicUrl);
        card.put("provider", provider);
        card.put("version", "0.1.0");
        card.put("protocolVersion", "0.2.0");
        card.put("capabilities", capabilities);
        card.put("defaultInputModes", List.of("text/plain"));
        card.put("defaultOutputModes", List.of("text/plain"));
        card.put("skills", List.of(qa, ruling));
        return card;
    }

    private static Map<String, Object> skill(
            String id,
            String name,
            String description,
            List<String> tags,
            List<String> examples
    ) {
        Map<String, Object> skill = new LinkedHashMap<>();
        skill.put("id", id);
        skill.put("name", name);
        skill.put("description", description);
        skill.put("tags", tags);
        skill.put("examples", examples);
        skill.put("inputModes", List.of("text/plain"));
        skill.put("outputModes", List.of("text/plain"));
        return skill;
    }
}
