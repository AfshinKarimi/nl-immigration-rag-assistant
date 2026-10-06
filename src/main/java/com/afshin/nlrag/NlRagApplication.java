    package com.afshin.nlrag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Entry point for the NL Immigration & Work RAG Assistant.
 *
 * <p>This service answers questions about Dutch immigration procedures and
 * labour law by retrieving relevant chunks from official documentation
 * (via hybrid vector + keyword search), reranking them for relevance, and
 * grounding an LLM's answer in that retrieved context — with every answer
 * citing its source document, page, and section.</p>
 */
@SpringBootApplication
@EnableCaching
public class NlRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(NlRagApplication.class, args);
    }
}
