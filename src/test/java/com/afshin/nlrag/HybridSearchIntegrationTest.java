package com.afshin.nlrag;

import com.afshin.nlrag.model.RetrievedChunk;
import com.afshin.nlrag.retrieval.HybridSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Hits real pgvector {@code <=>} and generated {@code tsvector} SQL.
 * Embeddings are stubbed so this test does not need Ollama.
 */
@Testcontainers
@SpringBootTest
class HybridSearchIntegrationTest {

    private static final int DIMS = 768;
    private static final UUID TARGET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID NOISE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @MockBean
    ChatModel chatModel;

    @MockBean
    EmbeddingModel embeddingModel;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("nlrag_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("init-test.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    HybridSearchService hybridSearchService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedChunksAndStubQueryEmbedding() {
        when(embeddingModel.embed(anyString())).thenReturn(targetEmbedding());

        jdbcTemplate.update("DELETE FROM document_chunks");
        insertChunk(
                TARGET_ID,
                "The 30% ruling lasts five years. See IND aanvraagformulier 7500 for the application.",
                "30-percent-ruling-faq.txt",
                "tax",
                targetEmbedding()
        );
        insertChunk(
                NOISE_ID,
                "Unrelated pasta recipe with tomatoes and basil.",
                "cookbook.txt",
                "food",
                noiseEmbedding()
        );
    }

    @Test
    void vectorSearchRanksTheMatchingEmbeddingFirst() {
        List<RetrievedChunk> hits = hybridSearchService.search("how long is the tax ruling", 5);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).id()).isEqualTo(TARGET_ID.toString());
        assertThat(hits.get(0).vectorScore()).isGreaterThan(0.9);
    }

    @Test
    void keywordSearchFindsTheExactFormCode() {
        List<RetrievedChunk> hits = hybridSearchService.search("aanvraagformulier 7500", 5);

        assertThat(hits.stream().map(RetrievedChunk::id).toList())
                .contains(TARGET_ID.toString());
        RetrievedChunk target = hits.stream()
                .filter(c -> TARGET_ID.toString().equals(c.id()))
                .findFirst()
                .orElseThrow();
        assertThat(target.keywordScore()).isGreaterThan(0);
        assertThat(target.content()).contains("7500");
    }

    private void insertChunk(UUID id, String content, String source, String section, float[] embedding) {
        jdbcTemplate.update(
                """
                        INSERT INTO document_chunks (id, content, metadata, embedding)
                        VALUES (?::uuid, ?, CAST(? AS jsonb), CAST(? AS vector))
                        """,
                id.toString(),
                content,
                "{\"source\":\"" + source + "\",\"section\":\"" + section + "\"}",
                toVectorLiteral(embedding)
        );
    }

    private static float[] targetEmbedding() {
        float[] v = new float[DIMS];
        v[0] = 1.0f;
        return v;
    }

    private static float[] noiseEmbedding() {
        float[] v = new float[DIMS];
        v[1] = 1.0f;
        return v;
    }

    private static String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }
}
