package com.afshin.nlrag.model;

import java.util.List;

/**
 * The response returned to the client: the generated answer, whether the
 * system was confident enough to answer directly, and the citations backing
 * the answer so a user can verify against the official source.
 */
public record AssistantAnswer(
        String question,
        String answer,
        boolean answeredWithConfidence,
        List<String> citations
) {

    public static AssistantAnswer lowConfidence(String question) {
        return new AssistantAnswer(
                question,
                "من به اندازه کافی از منابع رسمی مطمئن نیستم تا به این سوال جواب بدم. "
                        + "لطفاً برای اطلاعات دقیق به وب‌سایت رسمی IND یا یک مشاور مهاجرتی مراجعه کنید.",
                false,
                List.of()
        );
    }
}
