# NL Immigration & Work Assistant — RAG in Java

A Retrieval-Augmented Generation (RAG) assistant that answers questions
about Dutch immigration procedures and labour-law basics, grounded in
sample documentation and built to demonstrate **production-style RAG
engineering patterns** in Java — not just an LLM API wrapper.

> **Why this project:** researching the Dutch highly-skilled-migrant visa,
> the 30% tax ruling, and labour-contract basics meant digging through
> scattered PDFs and government pages. This project turns that research
> process into a reusable, source-cited assistant — and doubles as a
> concrete example of RAG system design end to end.

> **Disclaimer:** This is a **technical demonstration**, not legal or
> immigration advice. Always verify current rules on [ind.nl](https://ind.nl)
> / Belastingdienst, or consult a licensed adviser.

## What makes this different from a typical RAG demo

Most RAG tutorials stop at "embed some text, call an LLM." This project
also includes the parts that matter once you try to run something like it
in production:

- **Hybrid search** (dense vector + sparse keyword) instead of vector-only
  search, because exact terms (form codes, article numbers) matter in a
  legal/procedural domain.
- **A separate reranking stage** (cross-encoder) over a wide candidate set,
  trading extra compute for precision — only on the narrowed-down
  candidates, not the whole corpus.
- **A confidence threshold that can refuse to answer** rather than
  hallucinate, with every answer citing its source document, page, and
  section.
- **An agent layer, distinct from the fixed RAG pipeline** — the LLM
  decides per turn whether to search documents, run a date calculation, do
  both, or answer directly.
- **An MCP server** exposing those same tools over the Model Context
  Protocol, so any MCP-compatible client can call `searchImmigrationDocs`
  or `estimateRemaining30PercentRuling` without a bespoke REST client.
- **A2A façade** (Agent Card + JSON-RPC) so peer agents can delegate a
  task to this specialist — without a second RAG stack (ADR-11).
- **Local LLM and embeddings via Ollama** (`llama3.2` + `nomic-embed-text`)
  so chat and ingest do not depend on a cloud model API key.
- **Resilience patterns** (circuit breakers, retries, graceful fallback)
  around Ollama HTTP calls and the optional Cohere reranker.
- **Observability** — Micrometer metrics scraped by Prometheus (Actuator
  on a separate port), Grafana in compose.
- **Integration tests against a real pgvector Postgres** via Testcontainers.
- Written-up **architecture decisions with trade-offs** — see
  [`docs/architecture-decisions.md`](docs/architecture-decisions.md).

## Architecture

```
Ingestion:  Document Loader -> Chunking -> Ollama embeddings (nomic-embed-text)
            -> pgvector (HNSW, 768 dimensions)

Fixed pipeline (/api/assistant/ask):
  Hybrid Search (vector+keyword) -> Reranker -> Confidence check
  -> Prompt Builder -> Ollama LLM (llama3.2) -> Cited Answer

Agent (/api/agent/chat):
  LLM decides per turn which tool(s) to call:
    - searchImmigrationDocs        (wraps the fixed pipeline above)
    - calculateDateDifference
    - estimateRemaining30PercentRuling

MCP server (/sse):
  The same three tools, exposed over the Model Context Protocol.

A2A (/.well-known/agent-card.json + POST /a2a):
  Peer agents discover this service and send a task; handled by
  ImmigrationAgentService (same tools as MCP).
```

See [`docs/architecture-decisions.md`](docs/architecture-decisions.md) for
ADRs (hybrid search, confidence refuse, agent vs RAG, MCP, Ollama, A2A).

## Tech stack

| Layer | Choice |
| --- | --- |
| Language / runtime | Java 21 (virtual threads for concurrent ingestion) |
| Framework | Spring Boot 3.3 + Spring AI 1.0.0-M6 |
| Build | Maven |
| Vector store | PostgreSQL + pgvector (HNSW, 768-d) |
| Keyword search | Postgres full-text (`tsvector` / `ts_rank`) |
| Embeddings / LLM | Ollama: chat `llama3.2`, embed `nomic-embed-text` |
| Reranker | Optional Cohere Rerank API (falls back to hybrid order) |
| Agent / tools | Spring AI `@Tool` methods |
| MCP | Spring AI MCP server (SSE) |
| A2A | Agent Card + JSON-RPC 2.0 façade |
| Resilience | Resilience4j |
| Observability | Micrometer + Prometheus + Grafana |
| Testing | JUnit 5 + Testcontainers |

## Prerequisites

- Docker & Docker Compose
- JDK 21 + Maven (for local `mvn test` / package)
- Several GB of disk for Ollama models (`nomic-embed-text`, `llama3.2`)

Optional: copy [`.env.example`](.env.example) to `.env` to override Ollama URL or models.

## Run with Docker Compose

```bash
# 1. Start infrastructure + app
docker compose up -d postgres ollama prometheus grafana
docker exec nlrag-ollama ollama pull nomic-embed-text
docker exec nlrag-ollama ollama pull llama3.2
docker compose up -d --build app
# If Docker Hub builds are flaky, package locally and inject the JAR:
#   mvn -DskipTests package
#   docker compose up -d --no-build app
#   docker cp target/nl-immigration-rag-assistant-0.1.0.jar nlrag-app:/app/app.jar
#   docker restart nlrag-app

# Chunk table is vector(768). If you previously used another embedding size:
#   docker exec nlrag-postgres psql -U nlrag -d nlrag -c "DROP TABLE IF EXISTS document_chunks CASCADE;"
```

### Demo script

```bash
# Public API probe (Actuator is on 8081 inside Docker only)
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/.well-known/agent-card.json

# Ingest
curl -s -X POST http://localhost:8080/api/ingestion/upload \
  -F "file=@data/sample-docs/30-percent-ruling-faq.txt" \
  -F "section=tax"

# Fixed RAG — expect answeredWithConfidence true + citations
curl -s -X POST http://localhost:8080/api/assistant/ask \
  -H "Content-Type: application/json" \
  -d '{"question":"How long does the 30% ruling last?"}' | python3 -m json.tool

# Refuse — off-topic
curl -s -X POST http://localhost:8080/api/assistant/ask \
  -H "Content-Type: application/json" \
  -d '{"question":"What is the best pasta recipe?"}' | python3 -m json.tool

# Agent — date / remaining 30% ruling
curl -s -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"If my 30% ruling started 2022-03-01, how much time is left?"}'

# A2A
curl -s http://localhost:8080/.well-known/agent-card.json | python3 -m json.tool
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"message/send","params":{"message":{"role":"user","messageId":"m1","kind":"message","parts":[{"kind":"text","text":"How long does the 30% ruling last?"}]}}}' \
  | python3 -m json.tool

# MCP: npx @modelcontextprotocol/inspector — SSE URL http://localhost:8080/sse
```

Optional: ingest `data/sample-docs/ind-highly-skilled-migrant-digest.txt` with `-F section=ind-hsm`.

## Configuration

| Variable | Purpose | Default |
| --- | --- | --- |
| `SPRING_AI_OLLAMA_BASE_URL` | Ollama base URL | `http://ollama:11434` (compose) / `http://localhost:11434` |
| `OLLAMA_CHAT_MODEL` | Chat model | `llama3.2` |
| `OLLAMA_EMBED_MODEL` | Embedding model | `nomic-embed-text` |
| `A2A_PUBLIC_URL` | Agent Card `url` | `http://localhost:8080/a2a` |
| `SPRING_DATASOURCE_*` | Postgres JDBC | demo user/db `nlrag` (local only) |
| `nlrag.reranker.api-key` | Cohere Rerank (optional) | empty → hybrid-order fallback |

**Demo credentials in compose** (local only): Postgres `nlrag`/`nlrag`, Grafana `admin`/`admin`. Do not reuse these outside a local lab.

Actuator (`health`, `prometheus`) listens on **8081** inside the app container and is **not** published to the host. Prometheus scrapes `http://app:8081/actuator/prometheus`.

```bash
docker exec nlrag-app wget -qO- http://127.0.0.1:8081/actuator/health
```

Grafana: `http://localhost:3000` (admin/admin)

## Main HTTP surfaces

| Method | Path | Role |
| --- | --- | --- |
| `POST` | `/api/ingestion/upload` | Ingest allowlisted document (txt/pdf/html/docx) |
| `POST` | `/api/assistant/ask` | Fixed RAG pipeline |
| `POST` | `/api/agent/chat` | Tool-calling agent |
| `GET` | `/sse` | MCP SSE endpoint |
| `GET` | `/.well-known/agent-card.json` | A2A discovery |
| `POST` | `/a2a` | A2A JSON-RPC (`message/send`, `tasks/get`, …) |
| `GET` | `:8081/actuator/health` | Liveness (internal) |
| `GET` | `:8081/actuator/prometheus` | Metrics (internal) |

## Tests

```bash
mvn test
```

Covers chunking, upload allowlisting, A2A JSON-RPC, request constraints, and
`HybridSearchIntegrationTest` (real pgvector via Testcontainers: insert +
assert cosine/`tsvector` hits). Chat and embeddings are mocked so tests do
not need Ollama.

## Project structure

```
src/main/java/com/afshin/nlrag/
├── ingestion/     # Validate, Tika, chunk, VectorStore
├── retrieval/     # Hybrid search, rerank, confidence, RagQueryService
├── generation/    # Prompt + Ollama chat
├── tools/         # @Tool methods (agent + MCP)
├── agent/         # /api/agent/chat
├── mcp/           # MCP tool registration
├── a2a/           # Agent Card + JSON-RPC façade
├── observability/ # Micrometer
├── controller/    # REST (ask / ingest)
└── config/
docs/
├── architecture-decisions.md
└── evaluation-results.md
data/sample-docs/  # Demo corpus
sql/init.sql       # pgvector schema
observability/     # Prometheus scrape config
```

## Roadmap / possible extensions

- [ ] Automated evaluation harness for `docs/evaluation-results.md`
- [ ] Local cross-encoder reranker (remove optional Cohere dependency)
- [ ] Multi-language query support (Dutch / Farsi / English)
- [ ] Streaming agent responses (SSE)
- [ ] Multi-turn agent memory
- [ ] Split into microservices on Kubernetes (out of scope for this monolith)

## Author

Built by Afshin Karimi — Senior Software Engineer specializing in Java/Spring,
exploring production RAG architecture while relocating to the Netherlands.

[LinkedIn](https://www.linkedin.com/in/afshinkarimi) · Feedback and PRs welcome.
