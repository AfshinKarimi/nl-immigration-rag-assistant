# Architecture Decision Records (ADRs)

Short-form log of the non-obvious decisions in this project and why they
were made. The goal isn't to list every choice — it's to record the ones
where a reasonable engineer could have gone the other way.

## ADR-1: HNSW over IVFFlat for the vector index

**Decision:** Use an HNSW index in pgvector rather than IVFFlat.

**Why:** IVFFlat needs to be trained on representative data and its recall
degrades if the underlying data distribution shifts after training (e.g.
after ingesting a new batch of IND documents). HNSW builds and updates
incrementally and gives higher recall at a given latency for small-to-medium
corpora (this project's target is low tens of thousands of chunks, not tens
of millions). The trade-off is higher memory usage and slower index build
time — acceptable here since the corpus is small and ingestion is not
latency-sensitive.

## ADR-2: Hybrid search (vector + keyword) instead of pure vector search

**Decision:** Combine pgvector cosine similarity with Postgres full-text
search (`tsvector`/`ts_rank`), weighted 70/30 in favour of vector score.

**Why:** Immigration and labour-law text is full of exact terms that matter
— form codes (e.g. "aanvraagformulier 7500"), specific article numbers,
acronyms (IND, BSN, DUO). Dense embeddings are good at semantic similarity
but can under-rank a chunk that contains the *exact* term the user typed if
the surrounding context isn't semantically close. Keyword search is a cheap
safety net for exactly this failure mode. The weighting (70/30) was chosen
empirically and is a config value (`nlrag.retrieval`), not hardcoded, so it
can be tuned per corpus.

## ADR-3: A separate reranker stage, only over the Top-K

**Decision:** Retrieve a wide Top-K (default 50) with cheap hybrid search,
then rerank only that narrowed set with a cross-encoder before taking the
final Top-N (default 5).

**Why:** Cross-encoders that jointly attend over (query, document) pairs
are meaningfully more accurate than independently-computed embeddings, but
they don't scale to running over an entire corpus per query. Splitting
retrieval into a cheap high-recall stage and an expensive high-precision
stage is the standard way to get both properties without a latency blow-up.

## ADR-4: A confidence threshold that can refuse to answer

**Decision:** If the top reranked result's relevance score falls below a
configurable threshold (`nlrag.retrieval.min-confidence`), the system
returns a "not confident enough" response instead of calling the LLM.

**Why:** In a domain with real consequences (a wrong answer about visa
eligibility, say), a confident-sounding hallucination is worse than an
honest "I don't know, check the official source." This is a deliberate
trade-off of coverage for reliability.

## ADR-5: Recursive chunking over fixed-size chunking (default)

**Decision:** Default to `RecursiveChunkingStrategy` (paragraph -> sentence
-> hard cut) rather than `FixedSizeChunkingStrategy`, though both ship in
the codebase for comparison.

**Why:** Fixed-size chunking can split a legal clause mid-sentence, which
both hurts embedding quality (the embedding represents a fragment, not a
coherent idea) and hurts the reranker (harder to judge relevance of a
half-sentence). The cost is a slightly more complex chunker and marginally
uneven chunk sizes. See `docs/evaluation-results.md` for a quantitative
comparison methodology.

## ADR-6: Resilience4j circuit breakers around model HTTP calls

**Decision:** Wrap the embedding call and the LLM call each in their own
named circuit breaker + retry policy, with a fallback path in the reranker
that degrades to hybrid-search ordering rather than failing the request.

**Why:** Chat and embeddings go to **Ollama** over HTTP (`SPRING_AI_OLLAMA_BASE_URL`).
That is still a remote process: CPU-bound generation can stall, the container
can be down, or the first request after a model load can time out. Circuit
breakers and retries keep a slow Ollama from exhausting servlet threads.
The optional Cohere reranker is a separate cloud call; if it is missing or
fails, the pipeline keeps hybrid-search ranking instead of returning 500.
Reranking is a quality improvement, not a hard requirement for the pipeline
to function.

## ADR-7: Virtual threads for the ingestion pipeline

**Decision:** Use `Executors.newVirtualThreadPerTaskExecutor()` for batch
ingestion rather than a fixed platform-thread pool.

**Why:** Ingestion is I/O-bound (file parsing + Ollama embedding HTTP round-trips).
Java 21 virtual threads let each ingested document run on its own
lightweight thread without needing to hand-tune a pool size — appropriate
here since ingestion throughput isn't the bottleneck the query path is, and
simplicity was preferred over manual tuning.

## ADR-8: A separate agent layer, distinct from the fixed RAG pipeline

**Decision:** Add `ImmigrationAgentService`, which hands the LLM a set of
tools (`ImmigrationTools`) and lets it decide per turn which to call,
rather than only exposing the fixed Hybrid-Search -> Rerank -> Generate
pipeline in `RagQueryService`.

**Why:** RAG and "an agent" are not the same thing, and conflating them
hides that difference in a portfolio project. The fixed pipeline always
retrieves, always reranks, always generates from context — appropriate
when every question is a document lookup. The agent can instead recognize
that "how many days until my 30% ruling ends, if it started 2022-03-01?"
needs a date calculation, not a document search, and can chain a document
search with a calculation if the question needs both. Both entry points
are kept: `/api/assistant/ask` for the deterministic pipeline (predictable
latency, always cited), and `/api/agent/chat` for the agentic one (more
flexible, less predictable about which tool path it takes).

## ADR-9: Exposing tools over MCP, not just REST

**Decision:** Register the same `@Tool`-annotated methods in
`ImmigrationTools` as MCP tools via `McpServerConfig`, in addition to
the REST controllers.

**Why:** A REST API is only callable by something written specifically
against its shape. MCP (Model Context Protocol) is a standard other AI
tools already speak — Claude Desktop, IDE assistants, other agents. Once
`ImmigrationTools` is registered as an MCP server, any MCP-compatible
client can discover and call `searchImmigrationDocs` or
`estimateRemaining30PercentRuling` with no custom integration code. The
tool definitions live in exactly one place (`ImmigrationTools`); both the
in-process agent and the external MCP surface call the same methods, so
there's no duplicated tool logic to keep in sync.

## ADR-10: Ollama for chat and embeddings, not OpenAI

**Decision:** Use Spring AI's Ollama starter with local models `llama3.2`
(chat / tool-calling) and `nomic-embed-text` (embeddings). pgvector columns
are `vector(768)` to match nomic. There is no OpenAI chat or embedding
dependency in this project.

**Why:** The assistant must run without a paid cloud LLM key. Ollama keeps
inference on the same Docker Compose stack as Postgres. The trade-off is
higher local CPU/RAM use and slower first-token latency than a hosted API,
which is acceptable for a demo and for environments that cannot send
immigration text to a third-party model provider. The optional Cohere
rerank API is independent of this choice and can stay disabled.

## ADR-11: A2A as a peer-agent façade, MCP stays the tool protocol

**Decision:** Expose an Agent Card at `/.well-known/agent-card.json` and a
JSON-RPC 2.0 endpoint at `POST /a2a` (`message/send`, `tasks/get`,
`tasks/list`, `tasks/cancel`). Each task is completed by calling
`ImmigrationAgentService.chat` — the same agent the REST `/api/agent/chat`
path uses. MCP (`/sse`) is unchanged.

**Why:** A2A is agent-to-agent (task lifecycle, discovery via Agent Card).
MCP is agent-to-tool. This project already has tools; A2A lets a *different*
agent delegate a question here without a custom REST client. Streaming,
push notifications, and a second retrieval stack are out of scope. Capabilities
on the card advertise `streaming: false` and `pushNotifications: false` so
clients do not expect them.

**Trade-off:** Tasks live in memory (last 100) and complete synchronously.
That is enough for a demo peer; it is not a durable A2A server.

## Testing strategy

- **Unit tests** for pure logic (chunking strategies) with no external
  dependencies.
- **Integration tests** with Testcontainers against a *real* pgvector
  Postgres image. `HybridSearchIntegrationTest` inserts 768-d rows and
  asserts vector ranking (`<=>`) and keyword hits on generated
  `tsvector` — not a context-load smoke test.
- Deliberately **not** mocking the vector extension itself, since the
  distance-operator semantics are the part most worth verifying for real.
