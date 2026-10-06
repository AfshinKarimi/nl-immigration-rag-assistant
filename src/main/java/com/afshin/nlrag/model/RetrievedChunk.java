package com.afshin.nlrag.model;

/**
 * A single chunk of source content along with the citation metadata needed
 * to trace every generated answer back to an official document — required
 * for a domain (immigration / labour law) where auditability matters.
 */
public record RetrievedChunk(
        String id,
        String content,
        String source,
        Integer page,
        String section,
        double vectorScore,
        double keywordScore,
        double rerankScore
) {

    public String citation() {
        return "%s, p.%s (%s)".formatted(source, page == null ? "?" : page, section);
    }
}
