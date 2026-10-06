# Retrieval Evaluation

This project includes a lightweight evaluation harness for measuring
retrieval quality rather than eyeballing a handful of demo queries.

## Methodology

1. Build a small labelled test set: a list of `(question, expected_chunk_ids)`
   pairs drawn from the sample documents in `data/sample-docs/`.
2. For each question, run it through `HybridSearchService` and separately
   through `RagQueryService` (post-rerank), recording which chunk IDs came
   back in the Top-K and Top-N.
3. Compute standard retrieval metrics against the expected set:

   - **Precision@k** — of the k chunks returned, what fraction are relevant?
   - **Recall@k** — of all relevant chunks that exist, what fraction were
     retrieved in the top k?
   - **MRR (Mean Reciprocal Rank)** — how high up the first relevant result
     ranks, averaged across questions.

## What to compare

Run the same test set through each of these configurations and record the
metrics side by side:

| Configuration | Precision@5 | Recall@5 | MRR |
|---|---|---|---|
| Vector search only | _fill in_ | _fill in_ | _fill in_ |
| Hybrid search (vector + keyword), no rerank | _fill in_ | _fill in_ | _fill in_ |
| Hybrid search + reranker (full pipeline) | _fill in_ | _fill in_ | _fill in_ |
| Fixed-size chunking (baseline) | _fill in_ | _fill in_ | _fill in_ |
| Recursive chunking (default) | _fill in_ | _fill in_ | _fill in_ |

Filling this table in with your own numbers, once you've ingested a real
document set, is exactly the kind of concrete evidence worth including in
a LinkedIn write-up or a README — "added a reranker, precision@5 went from
X to Y" is a far stronger claim than "added a reranker."

## Suggested next step

Write a small `@Test` (or a standalone `EvaluationRunner` main class) that
loads the labelled question set from a JSON/CSV file, calls the pipeline,
and prints this table automatically so it can be regenerated after every
architecture change.
